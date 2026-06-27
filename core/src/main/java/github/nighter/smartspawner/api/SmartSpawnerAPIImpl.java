package github.nighter.smartspawner.api;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistry;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistryImpl;
import github.nighter.smartspawner.api.gui.SpawnerGuiLayoutProvider;
import github.nighter.smartspawner.api.impl.SpawnerDataModifierImpl;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerRemovalService;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of the SmartSpawnerAPI interface.
 */
public class SmartSpawnerAPIImpl implements SmartSpawnerAPI {

    private final SmartSpawner plugin;
    private final SpawnerItemFactory itemFactory;
    private final SpawnerManager spawnerManager;
    private final SpawnerRemovalService spawnerRemovalService;
    private final GuiLayoutRegistryImpl guiLayoutRegistry;
    private volatile SpawnerGuiLayoutProvider spawnerGuiLayoutProvider;

    public SmartSpawnerAPIImpl(SmartSpawner plugin) {
        this.plugin = plugin;
        this.itemFactory = new SpawnerItemFactory(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerRemovalService = plugin.getSpawnerRemovalService();
        this.guiLayoutRegistry = plugin.getGuiLayoutRegistry();
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
                new NamespacedKey(plugin, "item_spawner_material"), PersistentDataType.STRING);

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

        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public SpawnerDataDTO getSpawnerById(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        return spawnerData != null ? convertToDTO(spawnerData) : null;
    }

    @Override
    public List<SpawnerDataDTO> getAllSpawners() {
        return spawnerManager.getAllSpawners().stream()
                .map(SmartSpawnerAPIImpl::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SpawnerDataModifier getSpawnerModifier(String spawnerId) {
        if (spawnerId == null) {
            return null;
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        return spawnerData != null ? new SpawnerDataModifierImpl(spawnerData) : null;
    }

    @Override
    public CompletableFuture<Boolean> removeSpawner(String spawnerId) {
        if (spawnerId == null) {
            return CompletableFuture.completedFuture(false);
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerById(spawnerId);
        if (spawnerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        return spawnerRemovalService.removeSpawner(spawnerData);
    }

    @Override
    public CompletableFuture<Boolean> removeSpawner(Location location) {
        if (location == null) {
            return CompletableFuture.completedFuture(false);
        }

        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(location);
        if (spawnerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        return spawnerRemovalService.removeSpawner(spawnerData);
    }

    @Override
    public GuiLayoutRegistry getLayoutRegistry() {
        return guiLayoutRegistry;
    }

    @Override
    public void setSpawnerLayoutProvider(SpawnerGuiLayoutProvider provider) {
        this.spawnerGuiLayoutProvider = provider;
        plugin.getGuiLayoutConfig().setProvider(provider);
    }

    @Override
    public void clearSpawnerLayoutProvider() {
        this.spawnerGuiLayoutProvider = null;
        plugin.getGuiLayoutConfig().setProvider(null);
    }

    /**
     * Gets the currently active per-spawner layout provider.
     *
     * @return the provider, or null
     */
    public SpawnerGuiLayoutProvider getSpawnerLayoutProvider() {
        return spawnerGuiLayoutProvider;
    }

    /**
     * Converts SpawnerData to SpawnerDataDTO.
     *
     * @param spawnerData the spawner data to convert
     * @return the DTO representation
     */
    public static SpawnerDataDTO convertToDTO(SpawnerData spawnerData) {
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
