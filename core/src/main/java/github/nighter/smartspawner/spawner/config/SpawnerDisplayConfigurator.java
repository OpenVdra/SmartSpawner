package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/** Applies the complete entity or item template rendered by a vanilla spawner block. */
public final class SpawnerDisplayConfigurator {
    private SpawnerDisplayConfigurator() {
    }

    public static void applyMob(SmartSpawner plugin, CreatureSpawner spawner, EntityType entityType) {
        applyMob(plugin, spawner, null, entityType);
    }

    public static void applyMob(SmartSpawner plugin, CreatureSpawner spawner, String configName, EntityType entityType) {
        EntitySnapshot snapshot = plugin.getSpawnerSettingsConfig().getEntityDisplaySnapshot(configName, entityType);
        if (snapshot != null) {
            spawner.setSpawnedEntity(snapshot);
        } else {
            spawner.setSpawnedType(entityType);
        }
    }

    public static void applyItem(SmartSpawner plugin, CreatureSpawner spawner, Material material) {
        applyItem(plugin, spawner, null, material);
    }

    public static void applyItem(SmartSpawner plugin, CreatureSpawner spawner, String configName, Material material) {
        ItemStack displayItem = plugin.getItemSpawnerSettingsConfig().getDisplayItem(configName, material);
        spawner.setSpawnedType(EntityType.ITEM);
        // BlockStateMeta owns an unplaced, world-less spawner state. Paper's setSpawnedItem
        // implementation needs a real CraftWorld, so defer the complete item model until placement.
        if (spawner.isPlaced() && displayItem != null && displayItem.getType() != Material.AIR) {
            spawner.setSpawnedItem(displayItem);
        }
    }
}
