package github.nighter.smartspawner.commands.list.gui.list;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.commands.list.gui.worldselection.WorldSelectionHolder;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerListGUI implements Listener {
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final ListSubCommand listSubCommand;
    private final NamespacedKey worldNameKey;
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );
    private static final String patternString = "#([A-Za-z0-9]+)";

    public SpawnerListGUI(SmartSpawner plugin) {
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.listSubCommand = plugin.getListSubCommand();
        this.worldNameKey = new NamespacedKey(plugin, "world_name");
    }

    @EventHandler
    public void onWorldSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof WorldSelectionHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        // Handle back button for world selection (both local and remote when cross-server is enabled)
        if (clickedItem.getType() == Material.RED_STAINED_GLASS_PANE) {
            // Go back to server selection
            listSubCommand.openServerSelectionGUI(player);
            return;
        }

        String worldName = clickedItem.getItemMeta().getPersistentDataContainer().get(
                worldNameKey,
                PersistentDataType.STRING
        );
        if (worldName == null) return;

        if (isRemote) {
            listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, 1);
            return;
        }

        listSubCommand.openSpawnerListGUI(player, worldName, 1);
    }

    @EventHandler
    public void onSpawnerListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        // Get current state
        String worldName = holder.getWorldName();
        int currentPage = holder.getCurrentPage();
        int totalPages = holder.getTotalPages();
        FilterOption currentFilter = holder.getFilterOption();
        SortOption currentSort = holder.getSortType();
        String targetServer = holder.getTargetServer();
        boolean isRemote = holder.isRemoteServer();

        // Handle filter button click (works for both local and remote)
        if (event.getSlot() == 48) {
            // Cycle to next filter option
            FilterOption nextFilter = currentFilter.getNextOption();

            // Save user preference when they change filter (only for local)
            if (!isRemote) {
                listSubCommand.saveUserPreference(player, worldName, nextFilter, currentSort);
            }

            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, 1, nextFilter, currentSort);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, 1, nextFilter, currentSort);
            }
            return;
        }

        // Handle sort button click (works for both local and remote)
        if (event.getSlot() == 50) {
            // Cycle to next sort option
            SortOption nextSort = currentSort.getNextOption();

            // Save user preference when they change sort (only for local)
            if (!isRemote) {
                listSubCommand.saveUserPreference(player, worldName, currentFilter, nextSort);
            }

            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, 1, currentFilter, nextSort);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, 1, currentFilter, nextSort);
            }
            return;
        }

        // Handle navigation - works for both local and remote
        if (event.getSlot() == 45 && currentPage > 1) {
            // Previous page
            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, currentPage - 1);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, currentPage - 1, currentFilter, currentSort);
            }
            return;
        }

        if (event.getSlot() == 49) {
            // Save preference before going back to world selection (only for local)
            if (!isRemote) {
                listSubCommand.saveUserPreference(player, worldName, currentFilter, currentSort);
            }

            // Back to world selection
            if (isRemote) {
                listSubCommand.openWorldSelectionGUIForServer(player, targetServer);
            } else {
                listSubCommand.openWorldSelectionGUI(player);
            }
            return;
        }

        if (event.getSlot() == 53 && currentPage < totalPages) {
            // Next page
            if (isRemote) {
                listSubCommand.openSpawnerListGUIForServer(player, targetServer, worldName, currentPage + 1);
            } else {
                listSubCommand.openSpawnerListGUI(player, worldName, currentPage + 1, currentFilter, currentSort);
            }
            return;
        }


        // Handle spawner item click (management menu)
        if (isSpawnerItemSlot(event.getSlot()) && isSpawnerItem(event.getCurrentItem())) {
            handleSpawnerItemClick(player, event.getCurrentItem(), holder);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SpawnerListHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Save user preferences when closing the inventory
        String worldName = holder.getWorldName();
        FilterOption currentFilter = holder.getFilterOption();
        SortOption currentSort = holder.getSortType();

        // Save preference when they close the GUI
        listSubCommand.saveUserPreference(player, worldName, currentFilter, currentSort);
    }

    private boolean isSpawnerItemSlot(int slot) {
        // Check if slot is in the spawner display area (first 5 rows, excluding borders)
        return slot < 45;
    }

    private boolean isSpawnerItem(ItemStack item) {
        // Check if item is a spawner or mob head (used for spawner display)
        return item != null && SPAWNER_MATERIALS.contains(item.getType()) &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName();
    }

    private void handleSpawnerItemClick(Player player, ItemStack item, SpawnerListHolder holder) {
        if (holder.isRemoteServer()) {
            return;
        }

        // Extract spawner ID from the item name
        String displayName = item.getItemMeta().getDisplayName();
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(displayName);

        if (matcher.find()) {
            String spawnerId = matcher.group(1);
            SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);

            if (spawner != null) {
                listSubCommand.openSpawnerManagementGUI(
                        player,
                        spawnerId,
                        holder.getWorldName(),
                        holder.getCurrentPage()
                );
            } else {
                messageService.sendMessage(player, "list.teleport_failed");
            }
        }
    }
}
