package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The storage GUI {@link Listener}: the single Bukkit event surface for the paged virtual-inventory
 * view. It classifies each click by slot region, enforces the no-deposit / take-only rules, and
 * routes the work to its collaborators — it holds no business logic itself.
 *
 * <ul>
 *   <li>{@link StorageButtonHandler} — control-button clicks (nav, sell, sort, take-all, ...)</li>
 *   <li>{@link StorageBulkTransfer} — take-all / drop-page bulk withdraws (via the button handler)</li>
 *   <li>{@link StorageReconciler} — settling a native item take-out against the count-map</li>
 *   <li>{@link StoragePageEditor} — page-count / title / redraw bookkeeping</li>
 *   <li>{@link StorageBagFiller} — pure player-inventory fill math</li>
 * </ul>
 */
public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final StorageReconciler reconciler;
    private final StorageButtonHandler buttonHandler;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        StoragePageEditor pageEditor = new StoragePageEditor(plugin);
        this.reconciler = new StorageReconciler(pageEditor);
        StorageBulkTransfer bulkTransfer = new StorageBulkTransfer(plugin, pageEditor);
        this.buttonHandler = new StorageButtonHandler(plugin, pageEditor, reconciler, bulkTransfer);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) ||
                !(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();

        // Block ALL storage interactions while a sell is in progress.
        // This closes the race window where the storage GUI could be reopened (by the
        // reopenPreviousGui callback) before the async sell's item-removal step has run,
        // which would otherwise allow items to be taken from the virtual inventory twice –
        // once by the player and once by applySellResult.
        if (spawner.isSelling()) {
            event.setCancelled(true);
            plugin.getMessageService().sendMessage(player, "action_in_progress");
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }

        GuiLayout layout = holder.getLayout();

        // Player's own inventory (bottom rows, raw >= 54): allow native moves within the bag, but never
        // let items flow INTO storage. Shift-click (MOVE_TO_OTHER_INVENTORY) pushes into the top
        // inventory and a double-click gather (COLLECT_TO_CURSOR) can pull from it, so both are
        // cancelled here; every other action stays inside the player's inventory and needs no
        // reconcile because storage does not change.
        if (raw >= INVENTORY_SIZE) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        // Control buttons are fully handled by us, never natively.
        if (isControlSlot(raw, layout)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            buttonHandler.handleControlSlotClick(
                    player, raw, holder, spawner, event.getInventory(), event.getClick(), layout);
            return;
        }

        // Item region (raw 0..44): native take-out only, no deposit.
        if (isItemSlot(raw)) {
            // Filler slots (beyond capacity on a partial last page) are display-only.
            if (raw >= SpawnerStorageUI.usableItemSlots(spawner, holder.getCurrentPage())) {
                event.setCancelled(true);
                return;
            }
            if (isTakeOutAction(event.getAction())) {
                // Let Bukkit hand the item to the player, then reconcile the count-map next tick so
                // the exact clicked cells are debited (loot that arrives meanwhile is preserved).
                reconciler.scheduleReconcile(player, holder);
            } else {
                // PLACE_*, SWAP_WITH_CURSOR, HOTBAR_*, NOTHING, UNKNOWN – block anything that could
                // place an item into storage.
                event.setCancelled(true);
            }
            return;
        }

        // Unused control-row slots (raw 45..53 with no button): consume the click.
        event.setCancelled(true);
    }

    /**
     * Actions that only ever move items OUT of the clicked storage slot. Only these are allowed to run
     * natively on an item slot; everything else is a place/swap and is cancelled to enforce no-deposit.
     */
    private static boolean isTakeOutAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY, DROP_ONE_SLOT, DROP_ALL_SLOT, COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }

    /**
     * Settles an outstanding native take, if any. Exposed for {@code StorageUpdateService}, which must
     * flush before it repaints a viewer whose pending debit still diffs against the last painted image.
     */
    public void flushPendingReconcile(Player player, Inventory inventory, boolean repaint) {
        reconciler.flushPendingReconcile(player, inventory, repaint);
    }

    private boolean isControlSlot(int slot, GuiLayout layout) {
        return layout != null && layout.isSlotUsed(slot);
    }

    private boolean isItemSlot(int slot) {
        // First 45 slots (0-44) are for storage items
        return slot >= 0 && slot < STORAGE_SLOTS;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder)) {
            return;
        }
        // A drag can only ever PLACE items, so any drag touching the top (storage) inventory is a
        // deposit and is cancelled. A drag confined to the player's own inventory is allowed.
        for (int raw : event.getRawSlots()) {
            if (raw < INVENTORY_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        // Inventory close events already execute on the owning player's region thread.
        // Do not defer this work to the player's scheduler: after closing, a block-backed
        // inventory could belong to a different region and resolving its holder there
        // violates Folia's thread ownership rules.

        // Settle an undebited native take while the event still carries the storage inventory. The
        // backstop task runs a tick from now and would find none, so a close in the same tick as the
        // take would otherwise drop the debit and leave the items duplicated.
        if (event.getPlayer() instanceof Player player) {
            reconciler.flushPendingReconcile(player, inventory, false);
        }

        handleInventoryClose(holder);
    }

    /**
     * Disconnect backstop. CraftBukkit closes an open container before firing this event, so
     * {@link #onInventoryClose} normally settles the debit first; this covers the case where it does
     * not, since an undebited take here survives in the player's saved inventory.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        reconciler.flushPendingReconcile(player, player.getOpenInventory().getTopInventory(), false);
    }

    private void handleInventoryClose(StoragePageHolder holder) {
        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isStorageDirty()) {
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            spawner.clearStorageDirty();
        }
    }
}
