package github.nighter.smartspawner.commands.list.gui.serverselection;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles click events in the server selection GUI.
 */
public class ServerSelectionHandler implements Listener {
    private final SmartSpawner plugin;
    private final ListSubCommand listSubCommand;

    public ServerSelectionHandler(SmartSpawner plugin, ListSubCommand listSubCommand) {
        this.plugin = plugin;
        this.listSubCommand = listSubCommand;
    }

    @EventHandler
    public void onServerSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ServerSelectionHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        String serverName = meta.getPersistentDataContainer().get(listSubCommand.getServerNameKey(), PersistentDataType.STRING);

        if (serverName == null || serverName.isEmpty()) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Open world selection for the selected server
        listSubCommand.openWorldSelectionGUIForServer(player, serverName);
    }
}
