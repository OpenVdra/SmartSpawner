package github.nighter.smartspawner.commands.list.gui.adminstacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.utils.SpawnerLocationLockManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for admin stacker GUI interactions.
 */
public class AdminStackerHandler implements Listener {
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int BACK_SLOT = 22;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final SpawnerManagementGUI managementGUI;
    private final SpawnerLocationLockManager locationLockManager;

    public AdminStackerHandler(SmartSpawner plugin, SpawnerManagementGUI managementGUI) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.managementGUI = managementGUI;
        this.locationLockManager = plugin.getSpawnerLocationLockManager();
    }

    @EventHandler
    public void onAdminStackerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof AdminStackerHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        SpawnerData spawner = holder.getSpawnerData();
        if (spawner == null) {
            messageService.sendMessage(player, "list.teleport_failed");
            return;
        }

        if (plugin.getSpawnerRemovalService().isRemovalPending(spawner)) {
            messageService.sendMessage(player, "action_in_progress");
            player.closeInventory();
            return;
        }

        handleClick(
                player,
                spawner,
                holder.getWorldName(),
                holder.getListPage(),
                event.getSlot()
        );
    }

    private void handleClick(Player player, SpawnerData spawner, String worldName, int listPage, int slot) {
        if (slot == BACK_SLOT) {
            managementGUI.openManagementMenu(player, spawner.getSpawnerId(), worldName, listPage);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (slot == SPAWNER_INFO_SLOT) {
            return;
        }

        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            if (slot == DECREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, -STACK_AMOUNTS[i]);
                return;
            }
        }

        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            if (slot == INCREASE_SLOTS[i]) {
                handleStackChange(player, spawner, worldName, listPage, STACK_AMOUNTS[i]);
                return;
            }
        }
    }

    private void handleStackChange(
            Player player,
            SpawnerData spawner,
            String worldName,
            int listPage,
            int change
    ) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        Location location = spawner.getSpawnerLocation();
        if (!locationLockManager.tryLock(location)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            if (plugin.getSpawnerRemovalService().isRemovalPending(spawner) ||
                    spawnerManager.getSpawnerById(spawner.getSpawnerId()) != spawner ||
                    spawnerManager.getSpawnerByLocation(location) != spawner) {
                messageService.sendMessage(player, "action_in_progress");
                player.closeInventory();
                return;
            }

            int newStackSize = spawner.getStackSize() + change;
            if (newStackSize < 1) {
                newStackSize = 1;
            } else if (newStackSize > spawner.getMaxStackSize()) {
                newStackSize = spawner.getMaxStackSize();
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("max", String.valueOf(newStackSize));
                messageService.sendMessage(player, "spawner_stack_full", placeholders);
            }

            spawner.setStackSize(newStackSize);
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.updateLastInteractedPlayer(player.getName());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            new AdminStackerUI(plugin).openAdminStackerGui(player, spawner, worldName, listPage);
        } finally {
            locationLockManager.unlock(location);
        }
    }
}
