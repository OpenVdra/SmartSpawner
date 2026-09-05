package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Shared page-state helpers for the storage GUI: page-count math, in-place page navigation, title
 * refresh, the recovery reopen, and the post-removal bookkeeping that every withdraw path runs.
 * Collaborators ({@link StorageReconciler}, {@link StorageButtonHandler}, {@link StorageBulkTransfer})
 * delegate here so the page rules live in one place.
 */
class StoragePageEditor {
    private final SmartSpawner plugin;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    StoragePageEditor(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
    }

    int calculateTotalPages(SpawnerData spawner) {
        // Pages follow the display layout (frozen cells, holes included), not the packed item count,
        // so navigation matches what is actually rendered. The shared StoragePageHolder.totalPagesFor
        // is the single formula, so this cannot diverge from SpawnerStorageUI / StorageUpdateService.
        return StoragePageHolder.totalPagesFor(spawner.getVirtualInventory().getDisplaySlotCount());
    }

    void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        spawnerStorageUI.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, spawner, newPage, totalPages);
    }

    void updateInventoryTitle(Player player, SpawnerData spawner, int page, int totalPages) {
        String newTitle = plugin.getSpawnerStorageUI().getStorageTitle(spawner, page, totalPages);

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page);
        }
    }

    void openLootPage(Player player, SpawnerData spawner, int page) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        int totalPages = calculateTotalPages(spawner);
        final int finalPage = Math.max(1, Math.min(page, totalPages));
        Inventory pageInventory = spawnerStorageUI.createStorageInventory(player, spawner, finalPage, totalPages);

        // Log storage GUI opening
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(SpawnerEventType.SPAWNER_STORAGE_OPEN, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("page", finalPage)
                            .metadata("total_pages", totalPages)
            );
        }

        player.openInventory(pageInventory);
    }

    /**
     * Updates the page display after items are removed from storage. With {@code repaint} false the
     * display work is skipped, but the page counts, hologram, capacity flag and dirty marker still
     * run because the items really did leave storage.
     */
    void updatePageAfterRemoval(Player player, Inventory inventory,
                                SpawnerData spawner, StoragePageHolder holder, boolean repaint) {
        // Recalculate pages
        int newTotalPages = calculateTotalPages(spawner);
        int currentPage = holder.getCurrentPage();

        // Clamp to valid page range
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
        }
        holder.updateOldUsedSlots();

        if (repaint) {
            // Update display
            SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
            spawnerStorageUI.updateDisplay(inventory, spawner, adjustedPage, newTotalPages);

            // Update title if pages changed
            if (newTotalPages != currentPage || adjustedPage != currentPage) {
                updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);
            }
        }

        // Update hologram and other viewers
        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        clearCapacityIfBelow(spawner);

        // Mark as modified
        spawner.markStorageDirty();
    }

    /**
     * Clears the at-capacity flag when real (packed) occupancy has dropped below the ceiling. Uses
     * {@code getUsedSlots()} (real items), not the display layout, which can carry holes.
     */
    void clearCapacityIfBelow(SpawnerData spawner) {
        if (spawner.getMaxSpawnerLootSlots() > spawner.getVirtualInventory().getUsedSlots()
                && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }
    }
}
