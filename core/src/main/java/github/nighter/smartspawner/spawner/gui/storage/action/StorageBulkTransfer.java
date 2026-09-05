package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerDropAllEvent;
import github.nighter.smartspawner.api.events.SpawnerTakeAllEvent;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.logging.SpawnerEventType;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.model.ItemSignature;
import github.nighter.smartspawner.spawner.model.VirtualInventory;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two bulk-withdraw control actions: take-all (this page's items into the player's bag) and
 * drop-page (this page's items onto the ground). Both are transactional against the count-map and
 * scoped to the page the player is viewing, so they clear in place rather than pulling items up from
 * later pages. Bag-fill math is delegated to {@link StorageBagFiller}; page bookkeeping to
 * {@link StoragePageEditor}.
 */
class StorageBulkTransfer {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final StoragePageEditor pageEditor;

    StorageBulkTransfer(SmartSpawner plugin, StoragePageEditor pageEditor) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.pageEditor = pageEditor;
    }

    boolean handleTakeAllItems(Player player, Inventory sourceInventory) {
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        PlayerInventory playerInv = player.getInventory();

        int currentPage = holder.getCurrentPage();
        int startSlot = (currentPage - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Scope to the page the player is actually viewing (like drop-page), projected from the
        // count-map rather than the GUI slots. An empty page takes nothing, even if later pages hold items.
        Int2ObjectMap<ItemStack> pageDisplay =
                virtualInv.getDisplayPage(currentPage, StoragePageHolder.MAX_ITEMS_PER_PAGE);
        if (pageDisplay.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // Consolidate this page's cells by signature: what the current page holds.
        Map<ItemSignature, Long> pageAvailable = new HashMap<>();
        for (ItemStack item : pageDisplay.values()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            pageAvailable.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        if (pageAvailable.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // How much of THIS page the bag can accept, competing for the same slots as a real fill.
        Map<ItemSignature, Long> desired = StorageBagFiller.simulateBagFill(playerInv, pageAvailable.entrySet());
        if (desired.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        boolean hasTakeListeners = SpawnerTakeAllEvent.getHandlerList().getRegisteredListeners().length != 0;
        if (hasTakeListeners) {
            Map<Integer, ItemStack> projected = StorageBagFiller.projectToSlots(desired);
            SpawnerTakeAllEvent event = new SpawnerTakeAllEvent(player, spawner.getSpawnerLocation(), projected);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            desired = StorageBagFiller.consolidateSlots(event.getItems());
            if (desired.isEmpty()) return false;
        }

        // Atomic removal; place back exactly what was removed (dupe-safe against stale views).
        // While frozen with no addon rewriting the list, empty this page's exact cells (capped by bag
        // space) so the page clears in place instead of pulling items up from later pages. Otherwise
        // fall back to a by-signature take built from the (possibly addon-modified) desired amounts.
        Map<ItemSignature, Long> removed;
        if (!hasTakeListeners && virtualInv.isOrderFrozen()) {
            removed = takeFromPageCells(spawner, pageDisplay, startSlot, desired);
        } else {
            removed = spawner.takeItems(desired);
        }
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        long totalMoved = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            StorageBagFiller.addToPlayerInventory(playerInv, entry.getKey().getTemplate(), entry.getValue().intValue());
            totalMoved += entry.getValue();
        }
        final long totalMovedFinal = totalMoved;
        spawner.updateHologramData();
        player.updateInventory();

        int newTotalPages = pageEditor.calculateTotalPages(spawner);

        // Clamp current page to valid range (e.g., if on page 6 but only 5 pages remain)
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
            SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
            spawnerStorageUI.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        } else {
            // Same page: still repaint so the emptied slots clear.
            plugin.getSpawnerStorageUI().updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        }

        pageEditor.updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);

        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        pageEditor.clearCapacityIfBelow(spawner);
        spawner.markStorageDirty();

        // Log take all items action
        if (plugin.getSpawnerActionLogger() != null) {
            int itemsLeft = spawner.getVirtualInventory().getUsedSlots();
            plugin.getSpawnerActionLogger().log(SpawnerEventType.SPAWNER_ITEM_TAKE_ALL, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_taken", totalMovedFinal)
                            .metadata("items_left", itemsLeft)
            );
        }
        return true;
    }

    boolean handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return false;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int startSlot = (holder.getCurrentPage() - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Project the current page from the source of truth (count-map), not the GUI slots.
        Int2ObjectMap<ItemStack> pageDisplay =
                virtualInv.getDisplayPage(holder.getCurrentPage(), StoragePageHolder.MAX_ITEMS_PER_PAGE);
        if (pageDisplay.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> pageItems = new ArrayList<>(pageDisplay.values());
        boolean hasDropListeners = SpawnerDropAllEvent.getHandlerList().getRegisteredListeners().length != 0;

        if (hasDropListeners) {
            SpawnerDropAllEvent event = new SpawnerDropAllEvent(player, spawner.getSpawnerLocation(), pageItems);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            pageItems = event.getItems();
        }

        // Atomic removal; drop back exactly what was removed (dupe-safe against stale views).
        // While frozen with no addon rewriting the list, empty the page's exact cells so the acted
        // page clears in place instead of pulling items up from later pages. Otherwise fall back to a
        // by-signature take built from the (possibly addon-modified) projected list.
        Map<ItemSignature, Long> removed;
        if (!hasDropListeners && virtualInv.isOrderFrozen()) {
            removed = spawner.takeItemsFromCellRange(startSlot, StoragePageHolder.MAX_ITEMS_PER_PAGE);
        } else {
            Map<ItemSignature, Long> desired = new HashMap<>();
            for (ItemStack item : pageItems) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                    continue;
                }
                desired.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
            }
            if (desired.isEmpty()) {
                messageService.sendMessage(player, "spawner_storage_empty");
                return false;
            }
            removed = spawner.takeItems(desired);
        }
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> toDrop = new ArrayList<>();
        long itemsFoundCount = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            itemsFoundCount += remaining;
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                toDrop.add(stack);
                remaining -= amt;
            }
        }

        final long itemsFound = itemsFoundCount;

        dropItemsInDirection(player, toDrop);

        int newTotalPages = pageEditor.calculateTotalPages(spawner);
        if (holder.getCurrentPage() > newTotalPages) {
            holder.setCurrentPage(Math.max(1, newTotalPages));
        }
        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();

        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        pageEditor.clearCapacityIfBelow(spawner);
        spawner.markStorageDirty();

        // Log drop page items action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_dropped", itemsFound)
                            .metadata("page_number", holder.getCurrentPage())
            );
        }

        pageEditor.updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        return true;
    }

    private void dropItemsInDirection(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);

        Vector velocity = new Vector(
                sinYaw * cosPitch * 0.3,
                sinPitch * 0.3 + 0.1,
                cosYaw * cosPitch * 0.3
        );

        for (ItemStack item : items) {
            Item droppedItem = world.dropItem(dropLocation, item, drop -> {
                drop.setThrower(playerUUID);
                drop.setPickupDelay(40);
            });


            droppedItem.setVelocity(velocity);
        }
    }

    /**
     * Capacity-aware page take: removes from the exact display cells of the acted page, in slot order,
     * up to the per-signature amounts the bag accepted. Mirrors drop-page's frozen behaviour (cells
     * empty in place) but stops once each signature's bag budget is exhausted. Requires a frozen order.
     *
     * @param pageDisplay page cells keyed by page-relative slot (from {@code getDisplayPage})
     * @param startSlot   global display slot the page starts at ({@code (page - 1) * MAX_ITEMS_PER_PAGE})
     * @param desired     per-signature cap on how much to take (what the bag can accept)
     * @return signature to amount actually removed
     */
    private Map<ItemSignature, Long> takeFromPageCells(
            SpawnerData spawner, Int2ObjectMap<ItemStack> pageDisplay, int startSlot,
            Map<ItemSignature, Long> desired) {
        Map<ItemSignature, Long> removed = new HashMap<>();
        Map<ItemSignature, Long> budget = new HashMap<>(desired);
        for (int relativeSlot = 0; relativeSlot < StoragePageHolder.MAX_ITEMS_PER_PAGE; relativeSlot++) {
            ItemStack cell = pageDisplay.get(relativeSlot);
            if (cell == null || cell.getType() == Material.AIR || cell.getAmount() <= 0) {
                continue;
            }
            ItemSignature sig = VirtualInventory.getSignature(cell);
            long left = budget.getOrDefault(sig, 0L);
            if (left <= 0) {
                continue;
            }
            long want = Math.min(left, cell.getAmount());
            Map<ItemSignature, Long> cellRemoved = spawner.takeItemFromCell(startSlot + relativeSlot, want);
            long got = cellRemoved.getOrDefault(sig, 0L);
            if (got > 0) {
                removed.merge(sig, got, Long::sum);
                budget.merge(sig, -got, Long::sum);
            }
        }
        return removed;
    }
}
