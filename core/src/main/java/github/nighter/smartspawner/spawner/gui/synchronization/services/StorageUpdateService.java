package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Service responsible for handling storage GUI page updates.
 * Manages page transitions and title updates when storage contents change.
 */
public class StorageUpdateService {

    private static final int ITEMS_PER_PAGE = 45;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI spawnerStorageUI;

    // Cache for storage title format
    private String cachedStorageTitleFormat = null;

    public StorageUpdateService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        initializeCache();
    }

    /**
     * Initializes the cached storage title format.
     */
    private void initializeCache() {
        cachedStorageTitleFormat = languageManager.getGuiTitle("gui_title_storage");
    }

    /**
     * Reloads the cached storage title format.
     */
    public void reloadCache() {
        cachedStorageTitleFormat = null;
        initializeCache();
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
            // to prevent stale values in future calculations
            spawnerStorageUI.updateDisplay(inventory, spawner, currentPage, newTotalPages);
            holder.updateOldUsedSlots();
            viewer.updateInventory();
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
        if (cachedStorageTitleFormat == null) {
            cachedStorageTitleFormat = languageManager.getGuiTitle("gui_title_storage");
        }
        
        // Build base placeholders (always present)
        Map<String, String> placeholders = new java.util.HashMap<>(5);
        placeholders.put("current_page", String.valueOf(page));
        placeholders.put("total_pages", String.valueOf(totalPages));

        // OPTIMIZATION: Only compute entity placeholders if they exist in the title format
        if (cachedStorageTitleFormat.contains("{entity}") || cachedStorageTitleFormat.contains("{ᴇɴᴛɪᴛʏ}")) {
            String entityName;
            if (spawner.isItemSpawner()) {
                entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
            } else {
                entityName = languageManager.getFormattedMobName(spawner.getEntityType());
            }

            if (cachedStorageTitleFormat.contains("{entity}")) {
                placeholders.put("entity", entityName);
            }
            if (cachedStorageTitleFormat.contains("{ᴇɴᴛɪᴛʏ}")) {
                placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
            }
        }

        // OPTIMIZATION: Only compute amount if it exists in the title format
        if (cachedStorageTitleFormat.contains("{amount}")) {
            placeholders.put("amount", String.valueOf(spawner.getStackSize()));
        }

        return languageManager.getGuiTitle("gui_title_storage", placeholders);
    }
}
