package github.nighter.smartspawner.commands.editloot;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Click and close handling for the loot list and the item capture screen.
 *
 * <p>Screens are told apart by their {@link org.bukkit.inventory.InventoryHolder}, never by title, so
 * a renamed or translated title cannot break routing.</p>
 */
public class LootEditorHandler implements Listener {

    private final MessageService messageService;
    private final LootEditorService service;
    private final LootEditorUI ui;
    private final LootEditorDialogs dialogs;

    public LootEditorHandler(SmartSpawner plugin, LootEditorService service,
                             LootEditorUI ui, LootEditorDialogs dialogs) {
        this.messageService = plugin.getMessageService();
        this.service = service;
        this.ui = ui;
        this.dialogs = dialogs;
    }

    // ============== Loot list ==============

    @EventHandler
    public void onLootListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LootListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;

        LootEditorTarget target = holder.getTarget();
        String entryKey = holder.getEntryKey();

        if (!service.hasEntry(target, entryKey)) {
            messageService.sendMessage(player, "command.editloot.entry_missing");
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        if (slot < LootEditorUI.LOOT_START || slot > LootEditorUI.LOOT_END) return;

        List<String> lootKeys = service.listLootKeys(target, entryKey);
        int index = slot - LootEditorUI.LOOT_START;

        if (index >= lootKeys.size()) {
            // Only the single green pane directly after the last row adds another item.
            if (index != lootKeys.size() || lootKeys.size() >= LootEditorUI.LOOT_SIZE) return;
            click(player);
            ui.openItemCapture(player, new LootCaptureHolder(target,
                    LootCaptureHolder.Purpose.ADD_LOOT, entryKey, null));
            return;
        }

        String lootKey = lootKeys.get(index);
        if (event.getClick() == ClickType.RIGHT) {
            click(player);
            ui.openItemCapture(player, new LootCaptureHolder(target,
                    LootCaptureHolder.Purpose.REPLACE_LOOT, entryKey, lootKey));
            return;
        }

        click(player);
        dialogs.openEditForm(player, target, entryKey, lootKey);
    }

    // ============== Item capture ==============

    @EventHandler
    public void onCaptureClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LootCaptureHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean ownInventory = event.getClickedInventory() != event.getInventory();

        // Shift-clicking would push the item past the drop slot into the filler panes, so it is the
        // one action that has to be blocked on both sides of the window.
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (ownInventory) {
            return; // Rearranging their own inventory is fine.
        }

        int slot = event.getSlot();
        if (slot == LootEditorUI.CAPTURE_SLOT) {
            return; // The whole point of this screen: let the item in and out freely.
        }

        event.setCancelled(true);

        if (slot == LootEditorUI.CAPTURE_CANCEL) {
            click(player);
            returnCapturedItem(player, event.getInventory());
            ui.openLootList(player, holder.getTarget(), holder.getEntryKey());
            return;
        }

        if (slot == LootEditorUI.CAPTURE_CONFIRM) {
            ItemStack captured = event.getInventory().getItem(LootEditorUI.CAPTURE_SLOT);
            if (captured == null || captured.getType() == Material.AIR) {
                messageService.sendMessage(player, "command.editloot.capture_empty");
                return;
            }

            // Taken out of the inventory before continuing so the close handler cannot hand it back
            // to the player as well.
            event.getInventory().setItem(LootEditorUI.CAPTURE_SLOT, null);
            applyCapture(player, holder, captured);
        }
    }

    @EventHandler
    public void onCaptureClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LootCaptureHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        returnCapturedItem(player, event.getInventory());
    }

    /** Never keep the admin's item: whatever is still in the drop slot goes back to them. */
    private void returnCapturedItem(Player player, Inventory inventory) {
        ItemStack captured = inventory.getItem(LootEditorUI.CAPTURE_SLOT);
        if (captured == null || captured.getType() == Material.AIR) {
            return;
        }
        inventory.setItem(LootEditorUI.CAPTURE_SLOT, null);
        for (ItemStack leftover : player.getInventory().addItem(captured).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void applyCapture(Player player, LootCaptureHolder holder, ItemStack captured) {
        LootEditorTarget target = holder.getTarget();
        String entryKey = holder.getEntryKey();

        if (!service.hasEntry(target, entryKey)) {
            messageService.sendMessage(player, "command.editloot.entry_missing");
            returnItem(player, captured);
            player.closeInventory();
            return;
        }

        switch (holder.getPurpose()) {
            case ADD_LOOT ->
                // The dialog now owns the captured item: it collects amount/chance/durability, writes
                // the row, and hands the physical item back when it finishes.
                    dialogs.openAddForm(player, target, entryKey, captured);
            case REPLACE_LOOT -> {
                service.setLootItem(target, entryKey, holder.getLootKey(), captured);
                returnItem(player, captured);
                messageService.sendMessage(player, "command.editloot.loot_replaced",
                        Map.of("label", holder.getLootKey()));
                ui.openLootList(player, target, entryKey);
            }
        }
    }

    private void returnItem(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }
}
