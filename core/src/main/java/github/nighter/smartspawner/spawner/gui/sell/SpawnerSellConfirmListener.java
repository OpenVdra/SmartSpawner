package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Handles all click/close events for the sell-confirm GUI.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SELL FLOW  (sell confirmation ENABLED – default)
 * ─────────────────────────────────────────────────────────────────────────────
 * Entry: SpawnerMenuAction / SpawnerStorageAction  →  SpawnerSellConfirmUI.openSellConfirmGui()
 *
 *  1. openSellConfirmGui():
 *       • spawner.isSelling() – abort if a sell is already running for this spawner.
 *       • Creates SpawnerSellConfirmHolder inventory, opens it for the player.
 *
 *  2. Player clicks CONFIRM  →  handleConfirm():
 *       • spawner.isSelling() early-exit – duplicate confirm-click packets in the same tick
 *         are silently dropped before any onComplete lambda is created. Per-spawner by nature,
 *         so confirming spawner B is never blocked by an in-flight sell on spawner A.
 *       • If collectExp: handleExpBottleClick() runs synchronously here.
 *       • SpawnerSellManager.sellAllItems(player, spawner, onComplete) called.
 *         sellAllItems() owns the async chain from this point:
 *           a. spawner.startSelling() CAS false→true – real dupe guard.
 *              [LEAK RISK: always released in finally inside the location task]
 *           b. closeAllViewersInventory(spawner) – evicts ALL current viewers.
 *           c. Snapshot virtual inventory (safe while isSelling blocks mutations).
 *           d. Async thread: calculate SellResult (pure CPU, no Bukkit API).
 *           e. Location/main thread: applySellResult() – deposit money, remove items,
 *              update hologram, notify player.
 *           f. onComplete.run():
 *                • Scheduler.runEntityTask(player) → reopenPreviousGui() for the selling player
 *                  only (player's own region thread — required for openInventory/initMenu).
 *           g. spawner.stopSelling() released in finally.
 *
 *  3. Player clicks CANCEL  →  handleCancel():
 *       • reopenPreviousGui() immediately (sync).
 *
 *  4. Player presses ESCAPE (GUI close without clicking)  →  onInventoryClose():
 *       • No extra work needed; the sell never started.
 *
 *  5. Player disconnects while sell is in flight:
 *       • No extra cleanup needed. spawner.isSelling is released by its own finally chain.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SELL FLOW  (sell confirmation DISABLED – skip_sell_confirmation: true)
 * ─────────────────────────────────────────────────────────────────────────────
 * Entry: same as above  →  SpawnerSellConfirmUI.openSellConfirmGui()  (skip path)
 *
 *  1. openSellConfirmGui():
 *       • spawner.isSelling() guard (same as above).
 *       • If collectExp: handleExpBottleClick() runs synchronously here.
 *       • player.closeInventory() – close any currently open GUI first.
 *       • SpawnerSellManager.sellAllItems(player, spawner, null) called.
 *
 *  No confirm GUI is ever created, so:
 *       • SpawnerSellConfirmHolder is never instantiated.
 *       • handleConfirm() is never invoked.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STATE OBJECTS AND LEAK CHECKLIST
 * ─────────────────────────────────────────────────────────────────────────────
 *  spawner.selling (AtomicBoolean, owned by SpawnerSellManager)
 *    Set by   : spawner.startSelling() inside sellAllItems() – synchronously, before async
 *    Cleared by: spawner.stopSelling() in the location-thread finally block
 *               (runs even if player is offline or sell fails mid-way).
 *    Leak check: finally block guarantees release.
 *    Duplicate-click guard: handleConfirm() returns early if isSelling() is true,
 *               so no onComplete lambda is ever created for the rejected click.
 */
public class SpawnerSellConfirmListener implements Listener {

    private final SmartSpawner plugin;

    public SpawnerSellConfirmListener(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SpawnerSellConfirmHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SpawnerSellConfirmHolder confirmHolder = (SpawnerSellConfirmHolder) holder;
        SpawnerData spawner = confirmHolder.getSpawnerData();

        if (spawner == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        // OPTIMIZATION: Get layout once and check action based on clicked slot
        GuiLayout layout = confirmHolder.getLayout();
        if (layout == null) {
            player.closeInventory();
            return;
        }

        Optional<GuiButton> buttonOpt = layout.getButtonAtSlot(slot);
        if (!buttonOpt.isPresent()) {
            return;
        }

        GuiButton button = buttonOpt.get();
        String clickType = getClickTypeString(event.getClick());
        String action = button.getActionWithFallback(clickType);

        if (action == null) {
            return;
        }
        if ("none".equals(action)) {
            return;
        }
        if (!plugin.getGuiButtonInteractionService().tryUse(
                player, GuiLayoutType.SELL_CONFIRM_GUI, button)) {
            return;
        }

        switch (action) {
            case "cancel":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickType);
                handleCancel(player, spawner, confirmHolder.getPreviousGui());
                break;
            case "confirm":
                handleConfirm(player, spawner, confirmHolder.getPreviousGui(),
                        confirmHolder.isCollectExp(), button, clickType);
                break;
            default:
                // Info button or unknown action - do nothing
                break;
        }
    }

    private void handleCancel(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Reopen previous GUI
        reopenPreviousGui(player, spawner, previousGui);
    }

    private void handleConfirm(Player player, SpawnerData spawner,
                               SpawnerSellConfirmUI.PreviousGui previousGui, boolean collectExp,
                               GuiButton button, String clickType) {
        // Drop duplicate confirm-click packets (e.g. from a cheat client replaying within the
        // same tick). startSelling() is called synchronously inside sellAllItems() before any
        // async work, so isSelling() is already true when the second click is processed.
        // Returning here — before creating an onComplete lambda — ensures sellAllItems() is
        // never called with a callback that would reopen the GUI mid-sell on CAS rejection.
        if (spawner.isSelling()) {
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, button, clickType);
            return;
        }

        // Collect exp silently so we can send a single combined sell+exp message
        long expCollected = 0;
        long expMending = 0;
        if (collectExp) {
            long[] expData = plugin.getSpawnerMenuAction().collectExpSilently(player, spawner);
            expCollected = expData[0];
            expMending = expData[1];
        }

        // Callback runs on the spawner's region/main thread after the sell fully completes.
        // Defers the GUI reopen until the inventory is actually emptied, closing the race
        // window where a storage GUI could be reopened with stale (pre-removal) items.
        final long finalExpCollected = expCollected;
        final long finalExpMending = expMending;
        Runnable onComplete = () -> {
            if (spawner.getVirtualInventory().getUsedSlots() == 0) {
                plugin.getGuiButtonInteractionService().playSuccessSound(
                        player, button, clickType);
            } else {
                plugin.getGuiButtonInteractionService().playFailSound(
                        player, button, clickType);
            }
            // player.openInventory() -> ServerPlayer.initMenu() must run on the PLAYER's own
            // region thread on Folia/Canvas, NOT the global region thread (the sell completes on
            // the spawner location's thread, which may be a different region). Dispatch via the
            // player's entity scheduler; on Paper this falls back to the main thread. If the
            // player logged off mid-sell, the entity task simply never runs.
            Scheduler.runEntityTask(player, () -> reopenPreviousGui(player, spawner, previousGui));
        };

        plugin.getSpawnerSellManager().sellAllItems(
                player, spawner, onComplete, finalExpCollected, finalExpMending);
    }

    private String getClickTypeString(org.bukkit.event.inventory.ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left_click";
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "click";
        };
    }

    private void reopenPreviousGui(Player player, SpawnerData spawner, SpawnerSellConfirmUI.PreviousGui previousGui) {
        // Check if player is Bedrock
        boolean isBedrockPlayer = isBedrockPlayer(player);

        switch (previousGui) {
            case MAIN_MENU:
                if (isBedrockPlayer && plugin.getSpawnerMenuFormUI() != null) {
                    // Reopen FormUI for Bedrock players
                    plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                } else {
                    // Reopen standard GUI for Java players
                    plugin.getSpawnerMenuUI().openSpawnerMenu(player, spawner, true);
                }
                break;
            case STORAGE:
                // Storage GUI works the same for both Java and Bedrock
                org.bukkit.inventory.Inventory storageInventory = plugin.getSpawnerStorageUI()
                        .createStorageInventory(player, spawner, 1, -1);
                player.openInventory(storageInventory);
                break;
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null ||
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }
}
