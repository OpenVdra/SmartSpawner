package github.nighter.smartspawner.spawner.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Every mutation of a spawner's virtual inventory, serialized on its {@code inventoryLock}. Each
 * operation keeps the three views in step under that one lock: the count-map (the source of truth),
 * the accumulated {@link SpawnerSellValue}, and the {@code storageVersion} that storage GUIs poll to
 * decide whether to redraw. A sell in progress ({@link SpawnerData#isSelling()}) blocks every take.
 *
 * <p>Reads the spawner's live inventory, lock and version through it, so a replaced virtual inventory
 * ({@link SpawnerData#setVirtualInventory}) is always seen.</p>
 */
final class SpawnerStorageOps {

    private final SpawnerData spawner;
    private final SpawnerSellValue sellValue;

    /** When the last storage viewer closed, for the reorder grace window. 0 means never emptied. */
    private volatile long lastEmptyAt = 0L;

    SpawnerStorageOps(SpawnerData spawner, SpawnerSellValue sellValue) {
        this.spawner = spawner;
        this.sellValue = sellValue;
    }

    /** Adds already-consolidated items and folds their value into the running total. */
    void addItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        ReentrantLock lock = spawner.getInventoryLock();
        lock.lock();
        try {
            spawner.getVirtualInventory().addItems(items);
            sellValue.applyAdded(items);
            spawner.getStorageVersion().incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Transactional take: atomically removes up to {@code desired} of each signature and returns
     * exactly what was removed. This is the dupe-safe primitive for player-driven takes — callers give
     * the player back only what this reports, so two clicks racing on the lock simply see the second
     * get less or nothing rather than duping a stale view.
     */
    Map<ItemSignature, Long> take(Map<ItemSignature, Long> desired) {
        if (desired == null || desired.isEmpty() || spawner.isSelling()) {
            return Collections.emptyMap();
        }
        return removeUnderLock(() -> spawner.getVirtualInventory().removeUpTo(desired));
    }

    /**
     * Removes exactly the cells in {@code [startSlot, startSlot + count)} of the pinned display, so a
     * dropped page empties its own slots in place. Only meaningful while the order is frozen.
     */
    Map<ItemSignature, Long> takeCellRange(int startSlot, int count) {
        if (spawner.isSelling()) {
            return Collections.emptyMap();
        }
        return removeUnderLock(() -> spawner.getVirtualInventory().takeCellRange(startSlot, count));
    }

    /**
     * Removes up to {@code maxAmount} from the single display cell at {@code globalSlot}, leaving a
     * hole if it empties. Only meaningful while frozen.
     */
    Map<ItemSignature, Long> takeCell(int globalSlot, long maxAmount) {
        if (maxAmount <= 0 || spawner.isSelling()) {
            return Collections.emptyMap();
        }
        return removeUnderLock(() -> spawner.getVirtualInventory().takeSingleCell(globalSlot, maxAmount));
    }

    boolean remove(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        Map<ItemSignature, Long> consolidated = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) {
                continue;
            }
            consolidated.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        return remove(consolidated);
    }

    boolean remove(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        ReentrantLock lock = spawner.getInventoryLock();
        lock.lock();
        try {
            boolean removed = spawner.getVirtualInventory().removeItems(items);
            if (removed) {
                sellValue.applyRemoved(items);
                spawner.getStorageVersion().incrementAndGet();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    long getLastEmptyAt() {
        return lastEmptyAt;
    }

    void markEmptyNow() {
        this.lastEmptyAt = System.currentTimeMillis();
    }

    boolean isOrderFrozen() {
        return spawner.getVirtualInventory() != null && spawner.getVirtualInventory().isOrderFrozen();
    }

    /**
     * Pins the current display order for the first viewer. Re-sorts first when {@code resort} is true
     * (grace elapsed or never frozen), otherwise keeps the previous order for a fast, jump-free reopen.
     */
    void freezeOrder(boolean resort) {
        ReentrantLock lock = spawner.getInventoryLock();
        lock.lock();
        try {
            if (resort) {
                spawner.getVirtualInventory().unfreezeOrder();
            }
            spawner.getVirtualInventory().freezeOrder();
        } finally {
            lock.unlock();
        }
    }

    /** Re-sorts and re-pins the order for a new sort preference, bumping the version so viewers redraw. */
    void applySortPreference(Material sort) {
        spawner.setPreferredSortItem(sort);
        ReentrantLock lock = spawner.getInventoryLock();
        lock.lock();
        try {
            spawner.getVirtualInventory().unfreezeOrder();
            spawner.getVirtualInventory().sortItems(sort);
            spawner.getVirtualInventory().freezeOrder();
        } finally {
            lock.unlock();
        }
        spawner.getStorageVersion().incrementAndGet();
    }

    /** Runs {@code op} under the inventory lock, accounting for and versioning any removal. */
    private Map<ItemSignature, Long> removeUnderLock(Supplier<Map<ItemSignature, Long>> op) {
        ReentrantLock lock = spawner.getInventoryLock();
        lock.lock();
        try {
            Map<ItemSignature, Long> removed = op.get();
            if (!removed.isEmpty()) {
                sellValue.applyRemoved(removed);
                spawner.getStorageVersion().incrementAndGet();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }
}
