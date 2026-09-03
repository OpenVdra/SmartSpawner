package github.nighter.smartspawner.hooks.items.providers.mmoitems;

import github.nighter.smartspawner.hooks.items.providers.CustomItemProvider;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class MMOItemsProvider implements CustomItemProvider {

    private static final String SYNTAX = "an MMOItems item is written as mmoitems:TYPE:ID, "
            + "for example mmoitems:SWORD:CUTLASS";

    @Override
    public String getNamespace() {
        return "mmoitems";
    }

    @Override
    public String getPluginName() {
        return "MMOItems";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin mmoItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsPlugin != null && mmoItemsPlugin.isEnabled()) {
                Class.forName("net.Indyuce.mmoitems.MMOItems");
                return MMOItems.plugin != null;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
        return false;
    }

    @Override
    public ItemStack createItem(String id) {
        int separator = id.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException(SYNTAX);
        }

        String type = id.substring(0, separator).trim().toUpperCase(Locale.ROOT);
        String itemId = id.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
        if (type.isEmpty() || itemId.isEmpty()) {
            throw new IllegalArgumentException(SYNTAX);
        }

        if (MMOItems.plugin == null) {
            throw new IllegalArgumentException("MMOItems is installed but not running");
        }

        if (!MMOItems.plugin.getTypes().has(type)) {
            throw new IllegalArgumentException("MMOItems has no item type '" + type + "'");
        }

        ItemStack item = MMOItems.plugin.getItem(type, itemId);
        if (item == null) {
            throw new IllegalArgumentException("MMOItems has no " + type + " with id '" + itemId + "'");
        }
        return item;
    }

    @Override
    public String describe(ItemStack item) {
        String type = MMOItems.getTypeName(item);
        String id = MMOItems.getID(item);
        return type == null || id == null ? null : type + ":" + id;
    }
}
