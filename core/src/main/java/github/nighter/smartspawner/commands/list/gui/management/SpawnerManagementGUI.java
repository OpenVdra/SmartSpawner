package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.language.section.GuiLanguageSection;
import net.kyori.adventure.text.Component;
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

import java.util.Map;

public class SpawnerManagementGUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TELEPORT_SLOT = 10;
    private static final int OPEN_SPAWNER_SLOT = 12;
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
            messageService.sendMessage(player, "command.list.teleport_failed");
            return;
        }

        GuiLanguageSection gui = languageManager.commandGui(player);
        Component title = gui.title("list.manage.title");
        Inventory inv = Bukkit.createInventory(
            new SpawnerManagementHolder(spawnerId, worldName, listPage),
            INVENTORY_SIZE, title
        );
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        createActionItem(gui, inv, TELEPORT_SLOT, "list.manage.teleport", Material.ENDER_PEARL);
        createActionItem(gui, inv, OPEN_SPAWNER_SLOT, "list.manage.open_spawner", Material.ENDER_EYE);
        createActionItem(gui, inv, REMOVE_SLOT, "list.manage.remove", Material.BARRIER);

        createActionItem(
                gui,
                inv,
                BACK_SLOT,
                "navigation.back",
                Material.RED_STAINED_GLASS_PANE,
                previousMenuPlaceholder(gui, "spawner_list", "Spawner List")
        );
        player.openInventory(inv);
    }

    private void createActionItem(GuiLanguageSection gui, Inventory inv, int slot, String langKey, Material material) {
        createActionItem(gui, inv, slot, langKey, material, Map.of());
    }

    private void createActionItem(
            GuiLanguageSection gui,
            Inventory inv,
            int slot,
            String langKey,
            Material material,
            Map<String, String> placeholders
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(langKey + ".name", placeholders));
            meta.lore(gui.lore(langKey + ".lore", placeholders));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        if (item.getType() == Material.SPAWNER) ItemTooltipUtil.hideTooltip(item);
        inv.setItem(slot, item);
    }

    private Map<String, String> previousMenuPlaceholder(GuiLanguageSection gui, String menuKey, String defaultName) {
        String menuName = gui.configString(
                "navigation.menu_names." + menuKey,
                defaultName
        );
        return Map.of("previous_menu", menuName);
    }

}
