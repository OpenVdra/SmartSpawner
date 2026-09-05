package github.nighter.smartspawner.spawner.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter private int maxSlots;
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;
    private Material preferredSortMaterial;

    // Frozen slot layout: while a viewer is present the display is pinned per slot (each cell holds
    // its own amount), so takes hit the exact acted cells and leave holes instead of items sliding up.
    // A null cell is a permanent hole; loot fills holes then appends. The count-map stays the source
    // of truth for totals; these cells mirror it and are what the GUI renders. Guarded by orderLock:
    // some display reads run without the owning SpawnerData.inventoryLock.
    private final Object orderLock = new Object();
    @Getter
    private volatile boolean orderFrozen = false;
    private List<FrozenCell> frozenCells;

    /** One pinned display cell: a signature and how many of it sit in this exact slot (1..maxStack). */
    private static final class FrozenCell {
        final ItemSignature sig;
        long amount;

        FrozenCell(ItemSignature sig, long amount) {
            this.sig = sig;
            this.amount = amount;
        }
    }

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.sortedEntriesCache = null;
        this.preferredSortMaterial = null;
    }

    public static ItemSignature getSignature(ItemStack item) {
        return new ItemSignature(item);
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = Math.max(0, maxSlots);
    }

    /*
     * FAST PATH
     * Used for loading already-consolidated storage data.
     */
    public void addItem(ItemStack item, long amount) {
        if (item == null || amount <= 0) {
            return;
        }

        ItemSignature signature = getSignature(item);

        consolidatedItems.merge(signature, amount, Long::sum);

        sortedEntriesCache = null;

        if (orderFrozen) {
            synchronized (orderLock) {
                if (orderFrozen && frozenCells != null) {
                    insertLoot(signature, amount);
                }
            }
        }
    }

    /*
     * Bulk insert for already-consolidated storage data.
     */
    public void addItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        boolean changed = false;
        boolean frozen = orderFrozen && frozenCells != null;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amountValue = entry.getValue();

            if (amountValue <= 0) {
                continue;
            }

            consolidatedItems.merge(signature, amountValue, Long::sum);
            changed = true;

            if (frozen) {
                synchronized (orderLock) {
                    if (orderFrozen && frozenCells != null) {
                        insertLoot(signature, amountValue);
                    }
                }
            }
        }

        if (changed) {
            sortedEntriesCache = null;
        }
    }
    /**
     * Adds an already-consolidated entry: one item template plus its total count.
     *
     * @param template the item template, its own amount is ignored
     * @param amount   how many of that item are stored, ignored when not positive
     */
    public void addConsolidatedItem(ItemStack template, long amount) {
        addItem(template, amount);
    }

    public boolean removeItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        Map<ItemSignature, Long> toRemove = new HashMap<>(items.size());

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Number amountValue = entry.getValue();

            if (signature == null || amountValue == null) {
                continue;
            }

            long amount = amountValue.longValue();
            if (amount <= 0) {
                continue;
            }

            toRemove.merge(signature, amount, Long::sum);
        }

        if (toRemove.isEmpty()) {
            return true;
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            if (consolidatedItems.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                return false;
            }
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            consolidatedItems.computeIfPresent(entry.getKey(), (key, current) -> {
                long remaining = current - entry.getValue();
                return remaining <= 0 ? null : remaining;
            });
        }

        sortedEntriesCache = null;

        // Keep the pinned cells in sync (sell removes by signature): clear front-to-back.
        mirrorRemovalToCells(toRemove);

        return true;
    }

    /**
     * Current stored count for a single signature. Read-only.
     */
    public long available(ItemSignature signature) {
        if (signature == null) {
            return 0L;
        }
        return consolidatedItems.getOrDefault(signature, 0L);
    }

    /**
     * Atomic, non-failing removal: for each requested signature removes
     * {@code min(desired, available)} and returns the amount actually removed.
     * Unlike {@link #removeItems(Map)} it never rejects the whole batch, so it is
     * safe against stale views – a second caller acting on outdated display data
     * simply gets back an empty or reduced map instead of over-removing.
     *
     * <p>Callers must hold the owning {@code SpawnerData.inventoryLock} for the
     * removal to be atomic against concurrent mutations.
     *
     * @param desired signature to requested amount
     * @return signature to amount actually removed (only positive entries)
     */
    public Map<ItemSignature, Long> removeUpTo(Map<ItemSignature, Long> desired) {
        if (desired == null || desired.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ItemSignature, Long> removed = new HashMap<>(desired.size());
        boolean changed = false;

        for (Map.Entry<ItemSignature, Long> entry : desired.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long wantValue = entry.getValue();
            if (signature == null || wantValue == null || wantValue <= 0) {
                continue;
            }

            long want = wantValue;
            long[] takenHolder = new long[1];
            consolidatedItems.computeIfPresent(signature, (key, current) -> {
                long take = Math.min(current, want);
                takenHolder[0] = take;
                long remaining = current - take;
                return remaining <= 0 ? null : remaining;
            });

            if (takenHolder[0] > 0) {
                removed.put(signature, takenHolder[0]);
                changed = true;
            }
        }

        if (changed) {
            sortedEntriesCache = null;
            // take-all / generic removal is by signature: mirror front-to-back into the pinned cells.
            mirrorRemovalToCells(removed);
        }

        return removed;
    }

    /**
     * Mirrors an already-applied count-map removal (keyed by signature) into the pinned cells,
     * clearing front-to-back. Used by the by-signature removal paths (take-all, sell) so the display
     * cells stay equal to the count-map. No-op when not frozen. Slot-targeted takes
     * ({@link #takeCellRange}, {@link #takeSingleCell}) update their cells directly and skip this.
     */
    private void mirrorRemovalToCells(Map<ItemSignature, Long> removedBySignature) {
        if (removedBySignature.isEmpty()) {
            return;
        }
        synchronized (orderLock) {
            if (!orderFrozen || frozenCells == null) {
                return;
            }
            Map<ItemSignature, Long> need = new HashMap<>(removedBySignature);
            for (int i = 0; i < frozenCells.size() && !need.isEmpty(); i++) {
                FrozenCell cell = frozenCells.get(i);
                if (cell == null) {
                    continue;
                }
                Long want = need.get(cell.sig);
                if (want == null || want <= 0) {
                    continue;
                }
                long take = Math.min(cell.amount, want);
                long left = cell.amount - take;
                if (left <= 0) {
                    frozenCells.set(i, null);
                } else {
                    cell.amount = left;
                }
                long rem = want - take;
                if (rem <= 0) {
                    need.remove(cell.sig);
                } else {
                    need.put(cell.sig, rem);
                }
            }
        }
    }

    /**
     * Places freshly generated loot into the pinned cells, filling from the top: first merge into
     * existing partial cells of the same signature, then drop into the earliest empty holes (reusing
     * slots freed by takes), and only then append new cells at the end. Caller holds {@code orderLock}
     * and has verified the layout is frozen.
     */
    private void insertLoot(ItemSignature sig, long amount) {
        if (amount <= 0) {
            return;
        }
        int maxStackSize = sig.getMaxStackSize();
        if (maxStackSize <= 0) {
            return;
        }

        // 1) Merge up: top up existing partial stacks of this signature, front to back.
        for (int i = 0; i < frozenCells.size() && amount > 0; i++) {
            FrozenCell cell = frozenCells.get(i);
            if (cell == null || cell.amount >= maxStackSize || !cell.sig.equals(sig)) {
                continue;
            }
            long add = Math.min(maxStackSize - cell.amount, amount);
            cell.amount += add;
            amount -= add;
        }

        // 2) Fill holes: reuse the earliest empty cells left by takes.
        for (int i = 0; i < frozenCells.size() && amount > 0; i++) {
            if (frozenCells.get(i) != null) {
                continue;
            }
            long take = Math.min(maxStackSize, amount);
            frozenCells.set(i, new FrozenCell(sig, take));
            amount -= take;
        }

        // 3) Append at the end for whatever still does not fit.
        while (amount > 0 && frozenCells.size() < maxSlots) {
            long take = Math.min(maxStackSize, amount);
            frozenCells.add(new FrozenCell(sig, take));
            amount -= take;
        }
    }

    /**
     * Slot-targeted removal for dropping a whole page: clears every live cell in
     * {@code [startSlot, startSlot + count)} and mirrors the removal into the count-map. Cleared cells
     * become permanent holes so the rest of the page does not slide up. Returns what was removed
     * (signature to amount) for the caller to hand back to the player. Empty when not frozen.
     */
    public Map<ItemSignature, Long> takeCellRange(int startSlot, int count) {
        Map<ItemSignature, Long> removed = new HashMap<>();
        synchronized (orderLock) {
            if (!orderFrozen || frozenCells == null) {
                return removed;
            }
            int begin = Math.max(0, startSlot);
            int end = Math.min(frozenCells.size(), startSlot + count);
            for (int i = begin; i < end; i++) {
                FrozenCell cell = frozenCells.get(i);
                if (cell == null || cell.amount <= 0) {
                    continue;
                }
                removed.merge(cell.sig, cell.amount, Long::sum);
                decrementConsolidated(cell.sig, cell.amount);
                frozenCells.set(i, null);
            }
        }
        if (!removed.isEmpty()) {
            sortedEntriesCache = null;
        }
        return removed;
    }

    /**
     * Slot-targeted removal for taking from one clicked cell: removes up to {@code maxAmount} from the
     * cell at {@code globalSlot} (the exact slot the player clicked), leaving a hole if it empties, and
     * mirrors the removal into the count-map. Returns what was removed. Empty when not frozen, the slot
     * is out of range, or the cell is already a hole.
     */
    public Map<ItemSignature, Long> takeSingleCell(int globalSlot, long maxAmount) {
        Map<ItemSignature, Long> removed = new HashMap<>();
        if (maxAmount <= 0) {
            return removed;
        }
        synchronized (orderLock) {
            if (!orderFrozen || frozenCells == null
                    || globalSlot < 0 || globalSlot >= frozenCells.size()) {
                return removed;
            }
            FrozenCell cell = frozenCells.get(globalSlot);
            if (cell == null || cell.amount <= 0) {
                return removed;
            }
            long take = Math.min(cell.amount, maxAmount);
            removed.put(cell.sig, take);
            decrementConsolidated(cell.sig, take);
            long left = cell.amount - take;
            if (left <= 0) {
                frozenCells.set(globalSlot, null);
            } else {
                cell.amount = left;
            }
        }
        sortedEntriesCache = null;
        return removed;
    }

    private void decrementConsolidated(ItemSignature sig, long amount) {
        if (amount <= 0) {
            return;
        }
        consolidatedItems.computeIfPresent(sig, (key, current) -> {
            long remaining = current - amount;
            return remaining <= 0 ? null : remaining;
        });
    }

    public Int2ObjectMap<ItemStack> getDisplayPage(int page, int pageSize) {
        if (pageSize <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        int safePage = Math.max(1, page);
        int startSlot = (safePage - 1) * pageSize;
        return buildDisplaySection(startSlot, pageSize);
    }

    public Int2ObjectMap<ItemStack> getDisplayRange(int startSlot, int maxResults) {
        return buildDisplaySection(startSlot, maxResults);
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
        if (consolidatedItems.isEmpty()) {
            return 0;
        }

        // Quick estimate - not perfectly accurate but avoids full rebuilds
        int estimatedSlots = 0;
        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            long amount = entry.getValue();
            int maxStackSize = entry.getKey().getMaxStackSize();
            estimatedSlots += (int) Math.ceil((double) amount / maxStackSize);
            if (estimatedSlots >= maxSlots) {
                return maxSlots; // Cap at max slots
            }
        }
        return estimatedSlots;
    }

    /**
     * Sorts items with the specified material type prioritized first.
     * This method optimizes by only invalidating caches when necessary.
     * 
     * @param preferredMaterial The material to sort first, or null for no preference
     */
    public void sortItems(org.bukkit.Material preferredMaterial) {
        // Store the preferred material for future cache rebuilds
        this.preferredSortMaterial = preferredMaterial;
        
        // Clear the sorted cache to force re-sorting with new preference
        this.sortedEntriesCache = null;
        
        // Only proceed if we have items to sort
        if (consolidatedItems.isEmpty()) {
            return;
        }
        
        // Generate new sorted entries with preference
        if (preferredMaterial != null) {
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted((e1, e2) -> {
                    // Use getTemplateRef() to avoid cloning - we only need to read the type
                    boolean e1Preferred = e1.getKey().getMaterial() == preferredMaterial;
                    boolean e2Preferred = e2.getKey().getMaterial() == preferredMaterial;

                    if (e1Preferred && !e2Preferred) return -1;
                    if (!e1Preferred && e2Preferred) return 1;
                    
                    // Both preferred or both not preferred, sort by material name
                    return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
                })
                .collect(java.util.stream.Collectors.toList());
        } else {
            // No preference, sort alphabetically by material name
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getMaterialName()))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    private Int2ObjectMap<ItemStack> buildDisplaySection(int startSlot, int maxResults) {
        if (maxResults <= 0 || startSlot >= maxSlots) {
            return Int2ObjectMaps.emptyMap();
        }

        // While frozen, positions come from the pinned slot layout, not from re-packing item counts.
        if (orderFrozen) {
            Int2ObjectMap<ItemStack> frozen = buildFrozenSection(startSlot, maxResults);
            if (frozen != null) {
                return frozen;
            }
        }

        if (consolidatedItems.isEmpty()) {
            return Int2ObjectMaps.emptyMap();
        }

        int safeStart = Math.max(0, startSlot);
        int sectionLimit = Math.min(maxResults, maxSlots - safeStart);
        if (sectionLimit <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(Math.min(sectionLimit, 45));
        List<Map.Entry<ItemSignature, Long>> sortedEntries = getSortedEntries();

        int currentGlobalSlot = 0;
        int relativeSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntries) {
            if (relativeSlot >= sectionLimit || currentGlobalSlot >= maxSlots) {
                break;
            }

            ItemSignature sig = entry.getKey();
            int maxStackSize = sig.getMaxStackSize();
            if (maxStackSize <= 0) {
                continue;
            }

            long totalAmount = entry.getValue();
            int stacksForEntry = (int) Math.min(
                    Integer.MAX_VALUE,
                    (totalAmount + maxStackSize - 1L) / maxStackSize
            );

            if (currentGlobalSlot + stacksForEntry <= safeStart) {
                currentGlobalSlot += stacksForEntry;
                continue;
            }

            int stacksToSkip = Math.max(0, safeStart - currentGlobalSlot);
            long remainingAmount = totalAmount - ((long) stacksToSkip * maxStackSize);
            currentGlobalSlot += stacksToSkip;

            while (remainingAmount > 0 && relativeSlot < sectionLimit && currentGlobalSlot < maxSlots) {
                ItemStack displayItem = sig.getTemplate();
                displayItem.setAmount((int) Math.min(remainingAmount, maxStackSize));
                section.put(relativeSlot++, displayItem);

                remainingAmount -= maxStackSize;
                currentGlobalSlot++;
            }
        }

        return Int2ObjectMaps.unmodifiable(section);
    }

    /**
     * Pins the current display at the slot level, one cell per rendered stack with its own amount, so
     * positions stop moving while a viewer is present. A no-op when a layout is already pinned, so a
     * quick reopen within the reorder grace keeps the previous layout (the caller unfreezes first when
     * it wants a fresh re-sort). Callers hold {@code SpawnerData.inventoryLock}; the extra
     * {@code orderLock} guards the unlocked display reads.
     */
    public void freezeOrder() {
        synchronized (orderLock) {
            if (orderFrozen && frozenCells != null) {
                return;
            }
            frozenCells = buildCellsFromSortedEntries();
            orderFrozen = true;
        }
    }

    /** Releases the pinned layout so the next display re-sorts and re-packs by the sort preference. */
    public void unfreezeOrder() {
        synchronized (orderLock) {
            orderFrozen = false;
            frozenCells = null;
            sortedEntriesCache = null;
        }
    }

    /**
     * Number of display cells the current layout occupies (for page-count math). While frozen this is
     * the pinned layout length, trailing empty cells trimmed, so permanent holes still count toward
     * pages but a depleted tail does not add an empty page. Unfrozen it is the packed used-slot count.
     */
    public int getDisplaySlotCount() {
        if (orderFrozen) {
            synchronized (orderLock) {
                if (frozenCells != null) {
                    int last = -1;
                    for (int i = frozenCells.size() - 1; i >= 0; i--) {
                        if (frozenCells.get(i) != null) {
                            last = i;
                            break;
                        }
                    }
                    return last + 1;
                }
            }
        }
        return getUsedSlots();
    }

    /**
     * Expands the current (freshly sorted, packed) contents into one cell per stack, each carrying its
     * own amount. Called only when pinning a layout for the first time, so it ignores prior state.
     */
    private List<FrozenCell> buildCellsFromSortedEntries() {
        List<Map.Entry<ItemSignature, Long>> sorted = new ArrayList<>(consolidatedItems.entrySet());
        sortEntries(sorted);

        List<FrozenCell> cells = new ArrayList<>();
        for (Map.Entry<ItemSignature, Long> entry : sorted) {
            ItemSignature sig = entry.getKey();
            int maxStackSize = sig.getMaxStackSize();
            if (maxStackSize <= 0) {
                continue;
            }
            long remaining = entry.getValue();
            while (remaining > 0 && cells.size() < maxSlots) {
                long take = Math.min(maxStackSize, remaining);
                cells.add(new FrozenCell(sig, take));
                remaining -= take;
            }
        }
        return cells;
    }

    /**
     * Renders a page window straight from the pinned cells: each cell's slot and amount are read as
     * they stand, with no re-packing, so a hole left by a take stays a hole and nothing slides up.
     * Returns {@code null} if the layout was concurrently unfrozen so the caller can fall back.
     */
    private Int2ObjectMap<ItemStack> buildFrozenSection(int startSlot, int maxResults) {
        synchronized (orderLock) {
            if (!orderFrozen || frozenCells == null) {
                return null;
            }

            int safeStart = Math.max(0, startSlot);
            int sectionLimit = Math.min(maxResults, maxSlots - safeStart);
            if (sectionLimit <= 0) {
                return Int2ObjectMaps.emptyMap();
            }
            Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(Math.min(sectionLimit, 45));
            for (int relative = 0; relative < sectionLimit; relative++) {
                int global = safeStart + relative;
                if (global >= frozenCells.size()) {
                    break;
                }
                FrozenCell cell = frozenCells.get(global);
                if (cell != null && cell.amount > 0) {
                    section.put(relative, stackOf(cell.sig, cell.amount));
                }
            }
            return Int2ObjectMaps.unmodifiable(section);
        }
    }

    private ItemStack stackOf(ItemSignature sig, long amount) {
        ItemStack item = sig.getTemplate();
        item.setAmount((int) Math.min(amount, sig.getMaxStackSize()));
        return item;
    }

    private List<Map.Entry<ItemSignature, Long>> getSortedEntries() {
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            sortEntries(sortedEntriesCache);
        }
        return sortedEntriesCache;
    }

    private void sortEntries(List<Map.Entry<ItemSignature, Long>> entries) {
        if (preferredSortMaterial != null) {
            entries.sort((e1, e2) -> {
                boolean e1Preferred = e1.getKey().getMaterial() == preferredSortMaterial;
                boolean e2Preferred = e2.getKey().getMaterial() == preferredSortMaterial;

                if (e1Preferred && !e2Preferred) return -1;
                if (!e1Preferred && e2Preferred) return 1;

                return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
            });
            return;
        }

        entries.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
    }
}
