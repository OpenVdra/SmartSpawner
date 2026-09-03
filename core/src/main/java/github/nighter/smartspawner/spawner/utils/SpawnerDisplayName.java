package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SpawnerDisplayName {

    private SpawnerDisplayName() {
    }

    public static String of(SmartSpawner plugin, SpawnerData spawner) {
        if (spawner == null) return "";
        return spawner.isItemSpawner()
                ? ofItem(plugin, spawner.getConfigName(), spawner.getSpawnedItemMaterial())
                : ofMob(plugin, spawner.getEntityType());
    }

    public static String ofMob(SmartSpawner plugin, EntityType entityType) {
        return plugin.getLanguageManager().getFormattedMobName(entityType);
    }

    public static String ofItem(SmartSpawner plugin, String configName, Material fallback) {
        LanguageManager language = plugin.getLanguageManager();
        ItemStack template = plugin.getItemSpawnerSettingsConfig().getTemplate(configName, fallback);
        if (template == null) {
            return language.getVanillaItemName(fallback);
        }
        ItemMeta meta = template.getItemMeta();
        return meta != null && meta.hasDisplayName()
                ? language.getItemDisplayNameText(template)
                : language.getVanillaItemName(template.getType());
    }
}
