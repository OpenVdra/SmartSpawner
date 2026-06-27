package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SpawnerManagementGUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TELEPORT_SLOT = 10;
    private static final int OPEN_SPAWNER_SLOT = 12;
    private static final int STACK_SLOT = 14;
    private static final int REMOVE_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;

    public SpawnerManagementGUI(SmartSpawner plugin) {
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    /**
     * Open management menu for a local spawner.
     */
    public void openManagementMenu(Player player, String spawnerId, String worldName, int listPage) {
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner == null) {
            messageService.sendMessage(player, "list.teleport_failed");
            return;
        }

        String title = languageManager.commandGui().title("gui_title_spawner_management");
        Inventory inv = Bukkit.createInventory(
            new SpawnerManagementHolder(spawnerId, worldName, listPage),
            INVENTORY_SIZE, title
        );
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        createActionItem(inv, TELEPORT_SLOT, "spawner_management.teleport", Material.ENDER_PEARL);
        createActionItem(inv, OPEN_SPAWNER_SLOT, "spawner_management.open_spawner", Material.ENDER_EYE);
        createActionItem(inv, STACK_SLOT, "spawner_management.stack", Material.SPAWNER);
        createActionItem(inv, REMOVE_SLOT, "spawner_management.remove", Material.BARRIER);

        createActionItem(
                inv,
                BACK_SLOT,
                "general_navigation.back",
                Material.RED_STAINED_GLASS_PANE,
                previousMenuPlaceholder("spawner_list", "Spawner List")
        );
        player.openInventory(inv);
    }

    private void createActionItem(Inventory inv, int slot, String langKey, Material material) {
        createActionItem(inv, slot, langKey, material, Map.of());
    }

    private void createActionItem(
            Inventory inv,
            int slot,
            String langKey,
            Material material,
            Map<String, String> placeholders
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(languageManager.commandGui().name(langKey + ".name", placeholders));
            List<String> lore = Arrays.asList(
                    languageManager.commandGui().lore(langKey + ".lore", placeholders)
            );
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        if (item.getType() == Material.SPAWNER) ItemTooltipUtil.hideTooltip(item);
        inv.setItem(slot, item);
    }

    private Map<String, String> previousMenuPlaceholder(String menuKey, String defaultName) {
        String menuName = languageManager.commandGui().configString(
                "general_navigation.menu_names." + menuKey,
                defaultName
        );
        return Map.of("previous_menu", menuName);
    }

}
