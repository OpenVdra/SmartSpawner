package github.nighter.smartspawner.api;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import github.nighter.smartspawner.api.data.SpawnerRemovalOptions;
import github.nighter.smartspawner.api.data.SpawnerRemovalResult;
import github.nighter.smartspawner.api.impl.SpawnerDataModifierImpl;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the SmartSpawnerAPI interface.
 */
public class SmartSpawnerAPIImpl implements SmartSpawnerAPI {

    private final SmartSpawner plugin;
    private final SpawnerItemFactory itemFactory;

    public SmartSpawnerAPIImpl(SmartSpawner plugin) {
        this.plugin = plugin;
        this.itemFactory = new SpawnerItemFactory(plugin);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType) {
        return itemFactory.createSmartSpawnerItem(entityType);
    }

    @Override
    public ItemStack createSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createSmartSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return itemFactory.createVanillaSpawnerItem(entityType);
    }

    @Override
    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        return itemFactory.createVanillaSpawnerItem(entityType, amount);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial) {
        return itemFactory.createItemSpawnerItem(itemMaterial);
    }

    @Override
    public ItemStack createItemSpawnerItem(Material itemMaterial, int amount) {
        return itemFactory.createItemSpawnerItem(itemMaterial, amount);
    }

    @Override
    public boolean isSmartSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // A SmartSpawner is a spawner that is NOT vanilla and NOT an item spawner
        return !isVanillaSpawner(item) && !isItemSpawner(item);
    }

    @Override
    public boolean isVanillaSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "vanilla_spawner"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN);
    }

    @Override
    public boolean isItemSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "item_spawner_material"),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return null;
        }

        BlockState blockState = blockMeta.getBlockState();
        if (!(blockState instanceof CreatureSpawner cs)) {
            return null;
        }

        return cs.getSpawnedType();
    }

    @Override
    public Material getItemSpawnerMaterial(ItemStack item) {
        if (!isItemSpawner(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String materialName = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "item_spawner_material"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (materialName == null) {
            return null;
        }

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public SpawnerDataDTO getSpawnerByLocation(Location location) {
        if (location == null) {
            return null;
        }

        SpawnerData spawnerData = plugin.getSpawnerManager().getSpawnerByLocation(location);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public SpawnerDataDTO getSpawnerById(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = plugin.getSpawnerManager().getSpawnerById(spawnerId);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public List<SpawnerDataDTO> getAllSpawners() {
        return plugin.getSpawnerManager().getAllSpawners().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SpawnerDataModifier getSpawnerModifier(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = plugin.getSpawnerManager().getSpawnerById(spawnerId);
        return spawnerData != null ? new SpawnerDataModifierImpl(spawnerData) : null;
    }

    @Override
    public SpawnerRemovalResult removeSpawner(Location location) {
        return removeSpawner(location, SpawnerRemovalOptions.defaults());
    }

    @Override
    public SpawnerRemovalResult removeSpawner(Location location, SpawnerRemovalOptions options) {
        if (location == null) {
            return SpawnerRemovalResult.NOT_FOUND;
        }

        SpawnerData spawnerData = plugin.getSpawnerManager().getSpawnerByLocation(location);
        if (spawnerData == null) {
            return SpawnerRemovalResult.NOT_FOUND;
        }

        return plugin.getSpawnerRemovalService().removeSpawner(spawnerData, options);
    }

    @Override
    public SpawnerRemovalResult removeSpawner(String spawnerId) {
        return removeSpawner(spawnerId, SpawnerRemovalOptions.defaults());
    }

    @Override
    public SpawnerRemovalResult removeSpawner(String spawnerId, SpawnerRemovalOptions options) {
        if (spawnerId == null) {
            return SpawnerRemovalResult.NOT_FOUND;
        }

        SpawnerData spawnerData = plugin.getSpawnerManager().getSpawnerById(spawnerId);
        if (spawnerData == null) {
            return SpawnerRemovalResult.NOT_FOUND;
        }

        return plugin.getSpawnerRemovalService().removeSpawner(spawnerData, options);
    }

    /**
     * Converts SpawnerData to SpawnerDataDTO.
     *
     * @param spawnerData the spawner data to convert
     * @return the DTO representation
     */
    private SpawnerDataDTO convertToDTO(SpawnerData spawnerData) {
        return new SpawnerDataDTO(
                spawnerData.getSpawnerId(),
                spawnerData.getSpawnerLocation(),
                spawnerData.getEntityType(),
                spawnerData.getSpawnedItemMaterial(),
                spawnerData.getStackSize(),
                spawnerData.getMaxStackSize(),
                spawnerData.getBaseMaxStoragePages(),
                spawnerData.getBaseMinMobs(),
                spawnerData.getBaseMaxMobs(),
                spawnerData.getBaseMaxStoredExp(),
                spawnerData.getSpawnDelay()
        );
    }
}