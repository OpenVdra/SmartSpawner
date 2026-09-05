package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Service responsible for handling storage GUI page updates.
 * Manages page transitions and title updates when storage contents change.
 */
public class StorageUpdateService {

    private static final int ITEMS_PER_PAGE = 45;

    private final SmartSpawner plugin;
    private final SpawnerStorageUI spawnerStorageUI;

    public StorageUpdateService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
    }

    /**
     * Version-based refresh: re-renders one storage viewer only if its cached image is behind the
     * spawner's current {@code storageVersion}. Runs the version gate and the render on the viewer's
     * region thread, so a viewer that is already up to date costs only a cheap comparison. The
     * batched update task calls this once per tick for every storage viewer.
     *
     * @param viewer  the player viewing storage
     * @param spawner the spawner whose storage they are viewing
     */
    public void refreshStorageViewer(Player viewer, SpawnerData spawner) {
        Location loc = viewer.getLocation();
        if (loc == null) {
            return;
        }
        Scheduler.runLocationTask(loc, () -> {
            if (!viewer.isOnline()) {
                return;
            }

            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            if (!(openInv.getHolder(false) instanceof StoragePageHolder holder)) {
                return;
            }
            if (!holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                return;
            }

            // Settle an undebited native take before anything below repaints, since that debit diffs
            // the last painted image against the live slots. Before the version gate on purpose: the
            // flush repaints and bumps the version itself.
            plugin.getSpawnerStorageAction().flushPendingReconcile(viewer, openInv, true);

            // Authoritative gate on the region thread that owns this inventory.
            if (holder.getView().getRenderedVersion() >= spawner.getStorageVersion().get()) {
                return;
            }

            // Pages follow the display layout (frozen cells, holes included). holder.getOldUsedSlots()
            // already tracks the display slot count, so both sides use the same source.
            int oldPages = calculateTotalPages(holder.getOldUsedSlots());
            int newPages = calculateTotalPages(spawner.getVirtualInventory().getDisplaySlotCount());
            processStorageUpdateDirect(viewer, openInv, spawner, holder, oldPages, newPages);
        });
    }

    /**
     * Processes storage update for a viewer.
     *
     * @param viewer The player viewing storage
     * @param spawner The spawner data
     * @param oldTotalPages Previous total pages
     * @param newTotalPages New total pages
     */
    public void processStorageUpdate(Player viewer, SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        Location loc = viewer.getLocation();
        if (loc != null) {
            Scheduler.runLocationTask(loc, () -> {
                if (!viewer.isOnline()) {
                    return;
                }

                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv == null || !(openInv.getHolder(false) instanceof StoragePageHolder)) {
                    return;
                }

                StoragePageHolder holder = (StoragePageHolder) openInv.getHolder(false);
                processStorageUpdateDirect(viewer, openInv, spawner, holder, oldTotalPages, newTotalPages);
            });
        }
    }

    /**
     * Processes storage update directly on the correct thread.
     *
     * @param viewer The player
     * @param inventory The open inventory
     * @param spawner The spawner data
     * @param holder The storage page holder
     * @param oldTotalPages Previous total pages
     * @param newTotalPages New total pages
     */
    public void processStorageUpdateDirect(Player viewer, Inventory inventory, SpawnerData spawner,
                                           StoragePageHolder holder, int oldTotalPages, int newTotalPages) {
        int currentPage = holder.getCurrentPage();
        boolean pagesChanged = oldTotalPages != newTotalPages;
        
        if (!pagesChanged) {
            // Just update contents - no title update needed, but MUST update oldUsedSlots
            // to prevent stale values in future calculations. The diff renderer writes only the
            // changed slots itself, so no viewer.updateInventory() (a full-inventory resend).
            spawnerStorageUI.updateDisplay(inventory, spawner, currentPage, newTotalPages);
            holder.updateOldUsedSlots();
            return;
        }
        
        // Determine if current page is still valid
        boolean needsNewInventory = false;
        int targetPage = currentPage;
        
        if (currentPage > newTotalPages) {
            // Current page is out of bounds, set to last page
            targetPage = newTotalPages;
            holder.setCurrentPage(targetPage);
            needsNewInventory = true;
        } else {
            // Pages changed but current page is still valid, just update title
            needsNewInventory = true;
        }

        if (needsNewInventory) {
            try {
                // Update holder metadata first
                holder.setTotalPages(newTotalPages);
                holder.updateOldUsedSlots();
                
                // Update inventory title and contents
                String newTitle = getStorageTitle(spawner, targetPage, newTotalPages);
                viewer.getOpenInventory().setTitle(newTitle);
                spawnerStorageUI.updateDisplay(inventory, spawner, targetPage, newTotalPages);
            } catch (Exception e) {
                // Fall back to creating a new inventory
                Inventory newInv = spawnerStorageUI.createStorageInventory(
                        viewer,
                        spawner,
                        targetPage,
                        newTotalPages
                );
                viewer.closeInventory();
                viewer.openInventory(newInv);
            }
        } else {
            // Just update contents
            spawnerStorageUI.updateDisplay(inventory, spawner, targetPage, newTotalPages);
            viewer.updateInventory();
        }
    }

    /**
     * Calculates total pages based on item count.
     *
     * @param totalItems Total number of items
     * @return Number of pages needed
     */
    public int calculateTotalPages(int totalItems) {
        return totalItems <= 0 ? 1 : (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    }

    /**
     * Gets the formatted storage title with page information.
     *
     * @param spawner The spawner data
     * @param page Current page number
     * @param totalPages Total number of pages
     * @return Formatted title
     */
    private String getStorageTitle(SpawnerData spawner, int page, int totalPages) {
        return spawnerStorageUI.getStorageTitle(spawner, page, totalPages);
    }
}
