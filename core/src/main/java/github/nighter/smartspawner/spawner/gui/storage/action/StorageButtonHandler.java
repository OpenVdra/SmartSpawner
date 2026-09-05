package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.logging.SpawnerEventType;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.Optional;

import static github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI.PreviousGui.STORAGE;

/**
 * Dispatches a click on a storage control button to the matching action. Resolves the layout-declared
 * action string (never a hardcoded slot), runs the interaction-service gate, and either handles the
 * action inline (navigation, sell, collect-exp, sort, return, filter) or delegates the bulk withdraws
 * to {@link StorageBulkTransfer}.
 */
class StorageButtonHandler {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerMenuUI spawnerMenuUI;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerManager spawnerManager;
    private final StoragePageEditor pageEditor;
    private final StorageReconciler reconciler;
    private final StorageBulkTransfer bulkTransfer;

    StorageButtonHandler(SmartSpawner plugin, StoragePageEditor pageEditor,
                         StorageReconciler reconciler, StorageBulkTransfer bulkTransfer) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerManager = plugin.getSpawnerManager();
        this.pageEditor = pageEditor;
        this.reconciler = reconciler;
        this.bulkTransfer = bulkTransfer;
    }

    void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                SpawnerData spawner, Inventory inventory, ClickType clickType, GuiLayout layout) {
        // A native take from an earlier click in this same tick may still be undebited, and every
        // action below either repaints this page or reads the count-map as authoritative.
        reconciler.flushPendingReconcile(player, inventory, true);

        // OPTIMIZATION: Get button and action with click type fallback
        Optional<GuiButton> buttonOpt = layout.getButtonAtSlot(slot);
        if (buttonOpt.isEmpty()) {
            return;
        }

        var button = buttonOpt.get();
        String clickTypeString = getClickTypeString(clickType);
        String action = button.getActionWithFallback(clickTypeString);

        if (action == null || action.isEmpty()) {
            return;
        }
        if ("none".equals(action)) {
            return;
        }
        if (!plugin.getGuiButtonInteractionService().tryUse(
                player, GuiLayoutType.STORAGE_GUI, button)) {
            return;
        }

        // OPTIMIZATION: Handle actions based on action value, not button name
        switch (action) {
            case "sort_items":
                playActionResult(player, button, clickTypeString,
                        handleSortItemsClick(player, spawner, inventory));
                break;
            case "open_filter":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    pageEditor.updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory);
                }
                break;
            case "take_all":
                playActionResult(player, button, clickTypeString,
                        bulkTransfer.handleTakeAllItems(player, inventory));
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    pageEditor.updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory);
                }
                break;
            case "drop_page":
                playActionResult(player, button, clickTypeString,
                        bulkTransfer.handleDropPageItems(player, spawner, inventory));
                break;
            case "sell_all":
                handleSellAction(player, spawner, false, button, clickTypeString, inventory);
                break;
            case "sell_and_exp":
                handleSellAction(player, spawner, true, button, clickTypeString, inventory);
                break;
            case "collect_exp":
                playActionResult(player, button, clickTypeString,
                        handleCollectExpAction(player, spawner, inventory));
                break;
            case "return_main":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                handleReturnToMainMenu(player, spawner);
                break;
            case "none":
                // Display-only button — consume click, do nothing
                break;
            default:
                // Unknown action, log warning
                plugin.getLogger().warning("Unknown storage action: " + action);
                break;
        }
    }

    /**
     * Convert Bukkit ClickType to string for action lookup
     * OPTIMIZATION: Cached string values to avoid repeated string creation
     */
    private String getClickTypeString(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left_click";
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "left_click";
        };
    }

    /**
     * Handle sell action with optional exp collection
     * OPTIMIZATION: Extracted common sell logic to reduce code duplication
     */
    private void handleSellAction(Player player, SpawnerData spawner, boolean collectExp,
                                  GuiButton sourceButton, String sourceClickType, Inventory inventory) {
        if (!plugin.hasSellIntegration()) {
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        // Check if there are items to sell
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            if (collectExp && spawner.getSpawnerExp() > 0) {
                // No items to sell, but collect the stored exp without leaving the storage GUI
                boolean success = handleCollectExpAction(player, spawner, inventory);
                playActionResult(player, sourceButton, sourceClickType, success);
            } else {
                messageService.sendMessage(player, "spawner_storage_empty");
                plugin.getGuiButtonInteractionService().playFailSound(
                        player, sourceButton, sourceClickType);
            }
            return;
        }

        // Open confirmation GUI
        plugin.getSpawnerSellConfirmUI().openSellConfirmGui(
                player, spawner, STORAGE, collectExp, sourceButton, sourceClickType);
    }

    /**
     * Collects stored XP from the spawner while keeping the player on the storage GUI.
     */
    private boolean handleCollectExpAction(Player player, SpawnerData spawner, Inventory inventory) {
        boolean collected = plugin.getSpawnerMenuAction().tryCollectExpForPlayer(player, spawner);
        if (collected) {
            // Refresh button display so the XP counter updates to 0
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder != null) {
                plugin.getSpawnerStorageUI().updateDisplay(inventory, spawner, holder.getCurrentPage(), holder.getTotalPages());
            }
        }
        return collected;
    }

    /**
     * Handle return to main menu action
     */
    private void handleReturnToMainMenu(Player player, SpawnerData spawner) {
        player.closeInventory();
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private void openFilterConfig(Player player, SpawnerData spawner) {
        filterConfigUI.openFilterConfigGUI(player, spawner);
    }

    private boolean handleSortItemsClick(Player player, SpawnerData spawner, Inventory inventory) {
        // Validate loot config
        if (spawner.getLootConfig() == null || spawner.getLootConfig().getAllItems() == null) {
            return false;
        }

        var lootItems = spawner.getLootConfig().getAllItems();
        if (lootItems.isEmpty()) {
            return false;
        }

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Build sorted list of available materials
        var sortedLoot = lootItems.stream()
                .map(LootItem::material)
                .distinct() // Remove duplicates if any
                .sorted(Comparator.comparing(Material::name))
                .toList();

        if (sortedLoot.isEmpty()) {
            return false;
        }

        // Find next sort item
        Material nextSort;

        if (currentSort == null) {
            // No current sort, select first item
            nextSort = sortedLoot.getFirst();
        } else {
            // Find current item index
            int currentIndex = sortedLoot.indexOf(currentSort);

            if (currentIndex == -1) {
                // Current sort item not in list anymore, reset to first
                nextSort = sortedLoot.getFirst();
            } else {
                // Select next item (wrap around to first if at end)
                int nextIndex = (currentIndex + 1) % sortedLoot.size();
                nextSort = sortedLoot.get(nextIndex);
            }
        }

        // Apply the new sort preference: re-sorts and re-pins the frozen order, and bumps the storage
        // version so other viewers redraw in the new order.
        spawner.applySortPreference(nextSort);

        // Mark spawner as modified to save the preference
        spawner.markStorageDirty();
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

        // Update GUI display to reflect VirtualInventory state
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            pageEditor.updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        }

        // Log items sort action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(SpawnerEventType.SPAWNER_ITEMS_SORT, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("sort_item", nextSort.name())
                            .metadata("previous_sort", currentSort != null ? currentSort.name() : "none")
            );
        }
        return true;
    }

    private void playActionResult(Player player, GuiButton button, String clickType, boolean success) {
        if (success) {
            plugin.getGuiButtonInteractionService().playSuccessSound(player, button, clickType);
        } else {
            plugin.getGuiButtonInteractionService().playFailSound(player, button, clickType);
        }
    }
}
