package github.nighter.smartspawner.hooks.items.providers;

import org.bukkit.inventory.ItemStack;

public interface CustomItemProvider {

    String getNamespace();

    String getPluginName();

    boolean isAvailable();

    ItemStack createItem(String id);

    String describe(ItemStack item);
}
