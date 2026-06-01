package github.nighter.smartspawner.spawner.data;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.utils.ItemStackSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SpawnerFileHandler implements SpawnerStorage {
    private final SmartSpawner plugin;
    private final Logger logger;
    private File spawnerDataFile;
    private FileConfiguration spawnerData;

    private static final String DATA_VERSION_KEY = "data_version";
    private final int CURRENT_VERSION;

    private final Set<String> dirtySpawners = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedSpawners = ConcurrentHashMap.newKeySet();

    private volatile boolean isSaving = false;
    private Scheduler.Task saveTask = null;

    public SpawnerFileHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.CURRENT_VERSION = plugin.getDATA_VERSION();
        setupSpawnerDataFile();
        startSaveTask();
    }

    @Override
    public boolean initialize() {
        // Initialization already happens in constructor
        // This method exists for interface compliance
        return spawnerDataFile != null && spawnerDataFile.exists();
    }

    private void setupSpawnerDataFile() {
        spawnerDataFile = new File(plugin.getDataFolder(), "spawners_data.yml");
        if (!spawnerDataFile.exists()) {
            plugin.saveResource("spawners_data.yml", false);
        }

        spawnerData = YamlConfiguration.loadConfiguration(spawnerDataFile);

        int version = spawnerData.getInt(DATA_VERSION_KEY, 1);
        if (version < CURRENT_VERSION) {
            logger.info("Data version " + version + " detected. Current version is " + CURRENT_VERSION + ".");
            logger.info("A migration will be attempted when the plugin fully loads.");
        }
    }

    private void startSaveTask() {
        // Hardcoded 5-minute interval (5 * 60 * 20 = 6000 ticks)
        long intervalTicks = 6000L;

        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        saveTask = Scheduler.runTaskTimerAsync(() -> {
            plugin.debug("Running scheduled save task");
            flushChanges();
        }, intervalTicks, intervalTicks);
    }

    @Override
    public void markSpawnerModified(String spawnerId) {
        if (spawnerId != null) {
            dirtySpawners.add(spawnerId);
            deletedSpawners.remove(spawnerId);
        }
    }

    @Override
    public void markSpawnerDeleted(String spawnerId) {
        if (spawnerId != null) {
            deletedSpawners.add(spawnerId);
            dirtySpawners.remove(spawnerId);
        }
    }

    @Override
    public void flushChanges() {
        if (dirtySpawners.isEmpty() && deletedSpawners.isEmpty()) {
            plugin.debug("No changes to flush");
            return;
        }

        if (isSaving) {
            plugin.debug("Flush operation already in progress");
            return;
        }

        isSaving = true;
        plugin.debug("Flushing " + dirtySpawners.size() + " modified and " + deletedSpawners.size() + " deleted spawners");

        Scheduler.runTaskAsync(() -> {
            try {
                if (!dirtySpawners.isEmpty()) {
                    Set<String> toUpdate = new HashSet<>(dirtySpawners);
                    dirtySpawners.removeAll(toUpdate);

                    Map<String, SpawnerData> batch = new HashMap<>();
                    for (String id : toUpdate) {
                        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(id);
                        if (spawner != null) {
                            batch.put(id, spawner);
                        }
                    }

                    if (!batch.isEmpty()) {
                        saveSpawnerBatch(batch);
                    }
                }

                if (!deletedSpawners.isEmpty()) {
                    Set<String> toDelete = new HashSet<>(deletedSpawners);
                    deletedSpawners.removeAll(toDelete);

                    for (String id : toDelete) {
                        String path = "spawners." + id;
                        spawnerData.set(path, null);
                    }

                    if (!toDelete.isEmpty()) {
                        spawnerData.save(spawnerDataFile);
                    }
                }

                // Automatic ghost spawner check removed - use /ss clear ghost_spawners command instead
            } catch (Exception e) {
                plugin.getLogger().severe("Error during flush: " + e.getMessage());
                e.printStackTrace();

                for (String id : dirtySpawners) {
                    dirtySpawners.add(id);
                }
                for (String id : deletedSpawners) {
                    deletedSpawners.add(id);
                }
            } finally {
                isSaving = false;
            }
        });
    }

    private boolean saveSpawnerBatch(Map<String, SpawnerData> spawners) {
        if (spawners.isEmpty()) return true;

        try {
            ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
            if (spawnersSection == null) {
                spawnersSection = spawnerData.createSection("spawners");
            }

            spawnerData.set(DATA_VERSION_KEY, CURRENT_VERSION);

            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                String spawnerId = entry.getKey();
                SpawnerData spawner = entry.getValue();
                String path = "spawners." + spawnerId;
                Location loc = spawner.getSpawnerLocation();

                spawnerData.set(path + ".location", String.format("%s,%d,%d,%d",
                        loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

                spawnerData.set(path + ".entityType", spawner.getEntityType() != null ?
                        spawner.getEntityType().name() : null);

                // Save item spawner material if this is an item spawner
                if (spawner.isItemSpawner()) {
                    spawnerData.set(path + ".itemSpawnerMaterial", spawner.getSpawnedItemMaterial().name());
                } else {
                    spawnerData.set(path + ".itemSpawnerMaterial", null);
                }

                String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%d,%b",
                        Math.max(0, spawner.getSpawnerExp()),
                        spawner.getSpawnerActive(),
                        spawner.getSpawnerRange(),
                        spawner.getSpawnerStop().get(),
                        spawner.getSpawnDelay(),
                        spawner.getMaxSpawnerLootSlots(),
                        spawner.getMaxStoredExp(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner.getStackSize(),
                        spawner.getMaxStackSize(),
                        spawner.getLastSpawnTime(),
                        spawner.getIsAtCapacity());

                spawnerData.set(path + ".settings", settings);
                
                // Save last interacted player separately
                spawnerData.set(path + ".lastInteractedPlayer", spawner.getLastInteractedPlayer());

                // Save preferred sort item
                spawnerData.set(path + ".preferredSortItem", spawner.getPreferredSortItem() != null ?
                        spawner.getPreferredSortItem().name() : null);

                Set<Material> filteredItems = spawner.getFilteredItems();
                if (filteredItems != null && !filteredItems.isEmpty()) {
                    List<String> materials = filteredItems.stream()
                            .map(Material::name)
                            .collect(Collectors.toList());
                    spawnerData.set(path + ".filteredItems", String.join(",", materials));
                } else {
                    spawnerData.set(path + ".filteredItems", null);
                }

                VirtualInventory virtualInv = spawner.getVirtualInventory();
                if (virtualInv != null) {
                    Map<ItemSignature, Long> items = virtualInv.getConsolidatedItems();
                    List<String> serializedItems = ItemStackSerializer.serializeInventory(items);
                    spawnerData.set(path + ".inventory", serializedItems);
                }
            }

            spawnerData.save(spawnerDataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawner batch to file!");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Map<String, SpawnerData> loadAllSpawnersRaw() {
        Map<String, SpawnerData> loadedSpawners = new HashMap<>();

        ConfigurationSection spawnersSection = spawnerData.getConfigurationSection("spawners");
        if (spawnersSection == null) return loadedSpawners;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                // Use non-logging version and skip hopper restart during batch load
                SpawnerData spawner = loadSpawnerFromConfig(spawnerId, false, false);
                // Add to map even if null (world not loaded)
                loadedSpawners.put(spawnerId, spawner);
            } catch (Exception e) {
                plugin.debug("Error loading spawner " + spawnerId + ": " + e.getMessage());
                // Add null entry to indicate error
                loadedSpawners.put(spawnerId, null);
            }
        }

        return loadedSpawners;
    }

    @Override
    public SpawnerData loadSpecificSpawner(String spawnerId) {
        try {
            return loadSpawnerFromConfig(spawnerId, false);
        } catch (Exception e) {
            plugin.debug("Error loading spawner " + spawnerId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the raw location string for a spawner (used by WorldEventHandler)
     */
    @Override
    public String getRawLocationString(String spawnerId) {
        String path = "spawners." + spawnerId + ".location";
        return spawnerData.getString(path);
    }

    private SpawnerData loadSpawnerFromConfig(String spawnerId, boolean logErrors) {
        return loadSpawnerFromConfig(spawnerId, logErrors, true);
    }

    private SpawnerData loadSpawnerFromConfig(String spawnerId, boolean logErrors, boolean restartHopper) {
        String path = "spawners." + spawnerId;

        String locationString = spawnerData.getString(path + ".location");
        if (locationString == null) {
            if (logErrors) {
                logger.severe("Invalid location for spawner " + spawnerId);
            }
            return null;
        }

        String[] locParts = locationString.split(",");
        if (locParts.length != 4) {
            if (logErrors) {
                logger.severe("Invalid location format for spawner " + spawnerId);
            }
            return null;
        }

        org.bukkit.World world = Bukkit.getWorld(locParts[0]);
        if (world == null) {
            if (logErrors) {
                logger.severe("World not found for spawner " + spawnerId + ": " + locParts[0]);
            } else {
                plugin.debug("World not yet loaded for spawner " + spawnerId + ": " + locParts[0]);
            }
            return null;
        }

        Location location = new Location(world,
                Integer.parseInt(locParts[1]),
                Integer.parseInt(locParts[2]),
                Integer.parseInt(locParts[3]));

        String entityTypeString = spawnerData.getString(path + ".entityType");
        if (entityTypeString == null) {
            if (logErrors) {
                logger.severe("Missing entity type for spawner " + spawnerId);
            }
            return null;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeString);
        } catch (IllegalArgumentException e) {
            if (logErrors) {
                logger.severe("Invalid entity type for spawner " + spawnerId + ": " + entityTypeString);
            }
            return null;
        }

        // Check if this is an item spawner
        SpawnerData spawner;
        if (entityType == EntityType.ITEM) {
            String itemSpawnerMaterialString = spawnerData.getString(path + ".itemSpawnerMaterial");
            if (itemSpawnerMaterialString != null) {
                try {
                    Material itemMaterial = Material.valueOf(itemSpawnerMaterialString);
                    spawner = new SpawnerData(spawnerId, location, itemMaterial, plugin);
                } catch (IllegalArgumentException e) {
                    if (logErrors) {
                        logger.severe("Invalid item spawner material for spawner " + spawnerId + ": " + itemSpawnerMaterialString);
                    }
                    return null;
                }
            } else {
                // Fallback to regular entity spawner if no item material specified
                spawner = new SpawnerData(spawnerId, location, entityType, plugin);
            }
        } else {
            spawner = new SpawnerData(spawnerId, location, entityType, plugin);
        }

        String settingsString = spawnerData.getString(path + ".settings");
        if (settingsString != null) {
            String[] settings = settingsString.split(",");

            int version = spawnerData.getInt(DATA_VERSION_KEY, 1);

            try {
                if (version >= 3) {
                    if (settings.length >= 13) {
                        spawner.setSpawnerExpData(parseClampedLong(settings[0], 0L, Long.MAX_VALUE));
                        spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                        spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                        spawner.getSpawnerStop().set(Boolean.parseBoolean(settings[3]));
                        spawner.setSpawnDelay(parseClampedLong(settings[4], 1L, Long.MAX_VALUE));
                        spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                        spawner.setMaxStoredExp(parseClampedLong(settings[6], 0L, Long.MAX_VALUE));
                        spawner.setMinMobs(Integer.parseInt(settings[7]));
                        spawner.setMaxMobs(Integer.parseInt(settings[8]));
                        // Load maxStackSize BEFORE stackSize so the saved limit is in place
                        // when setStackSize validates the value, preventing data loss if the
                        // global config limit was lowered after this spawner was saved.
                        spawner.setMaxStackSize(parseClampedInt(settings[10], 1, Integer.MAX_VALUE));
                        spawner.setStackSize(parseClampedInt(settings[9], 1, Integer.MAX_VALUE), restartHopper);
                        spawner.setLastSpawnTime(Long.parseLong(settings[11]));
                        spawner.setIsAtCapacity(Boolean.parseBoolean(settings[12]));
                    }
                } else {
                    spawner.setSpawnerExpData(parseClampedLong(settings[0], 0L, Long.MAX_VALUE));
                    spawner.setSpawnerActive(Boolean.parseBoolean(settings[1]));
                    spawner.setSpawnerRange(Integer.parseInt(settings[2]));
                    spawner.getSpawnerStop().set(Boolean.parseBoolean(settings[3]));
                    spawner.setSpawnDelay(parseClampedLong(settings[4], 1L, Long.MAX_VALUE));
                    spawner.setMaxSpawnerLootSlots(Integer.parseInt(settings[5]));
                    spawner.setMaxStoredExp(parseClampedLong(settings[6], 0L, Long.MAX_VALUE));
                    spawner.setMinMobs(Integer.parseInt(settings[7]));
                    spawner.setMaxMobs(Integer.parseInt(settings[8]));
                    spawner.setStackSize(parseClampedInt(settings[9], 1, Integer.MAX_VALUE), restartHopper);
                    spawner.setLastSpawnTime(Long.parseLong(settings[10]));
                    spawner.setIsAtCapacity(false);
                }
            } catch (NumberFormatException e) {
                logger.severe("Invalid settings format for spawner " + spawnerId);
                logger.severe("Settings: " + settingsString);
                e.printStackTrace();
                return null;
            }
        }

        String filteredItemsStr = spawnerData.getString(path + ".filteredItems");
        if (filteredItemsStr != null && !filteredItemsStr.isEmpty()) {
            String[] materialNames = filteredItemsStr.split(",");
            for (String materialName : materialNames) {
                try {
                    Material material = Material.valueOf(materialName.trim());
                    spawner.getFilteredItems().add(material);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid material in filtered items for spawner " + spawnerId + ": " + materialName);
                }
            }
        }

        List<String> inventoryData = spawnerData.getStringList(path + ".inventory");
        VirtualInventory virtualInv = new VirtualInventory(spawner.getMaxSpawnerLootSlots());

        if (inventoryData != null && !inventoryData.isEmpty()) {
            try {
                Map<ItemStack, Integer> items = ItemStackSerializer.deserializeInventory(inventoryData);
                for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
                    ItemStack item = entry.getKey();
                    int amount = entry.getValue();

                    if (item != null && amount > 0) {
                        while (amount > 0) {
                            int batchSize = Math.min(amount, item.getMaxStackSize());
                            ItemStack batch = item.clone();
                            batch.setAmount(batchSize);
                            virtualInv.addItems(Collections.singletonList(batch));
                            amount -= batchSize;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error loading inventory for spawner " + spawnerId);
                e.printStackTrace();
            }
        }

        spawner.setVirtualInventory(virtualInv);
        spawner.markSellValueDirty();

        // Load last interacted player
        String lastInteractedPlayer = spawnerData.getString(path + ".lastInteractedPlayer");
        spawner.setLastInteractedPlayer(lastInteractedPlayer);

        // Load preferred sort item
        String preferredSortItemStr = spawnerData.getString(path + ".preferredSortItem");
        if (preferredSortItemStr != null && !preferredSortItemStr.isEmpty()) {
            try {
                Material preferredSortItem = Material.valueOf(preferredSortItemStr);
                spawner.setPreferredSortItem(preferredSortItem);
                // Apply the sort preference to the virtual inventory
                virtualInv.sortItems(preferredSortItem);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid preferred sort item for spawner " + spawnerId + ": " + preferredSortItemStr);
            }
        }
        
        // Restore the physical spawner block state for item spawners
        if (spawner.isItemSpawner()) {
            Scheduler.runLocationTask(location, () -> {
                org.bukkit.block.Block block = location.getBlock();
                if (block.getType() == Material.SPAWNER) {
                    org.bukkit.block.BlockState state = block.getState(false);
                    if (state instanceof org.bukkit.block.CreatureSpawner cs) {
                        cs.setSpawnedType(EntityType.ITEM);
                        ItemStack spawnedItem = new ItemStack(spawner.getSpawnedItemMaterial(), 1);
                        cs.setSpawnedItem(spawnedItem);
                        cs.update(true, false);
                    }
                }
            });
        }
        
        return spawner;
    }

    @Override
    public void queueSpawnerForSaving(String spawnerId) {
        markSpawnerModified(spawnerId);
    }

    @Override
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }

        if (!dirtySpawners.isEmpty() || !deletedSpawners.isEmpty()) {
            try {
                isSaving = true;

                if (!dirtySpawners.isEmpty()) {
                    Map<String, SpawnerData> batch = new HashMap<>();
                    for (String id : dirtySpawners) {
                        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(id);
                        if (spawner != null) {
                            batch.put(id, spawner);
                        }
                    }

                    if (!batch.isEmpty()) {
                        saveSpawnerBatch(batch);
                    }
                }

                if (!deletedSpawners.isEmpty()) {
                    for (String id : deletedSpawners) {
                        String path = "spawners." + id;
                        spawnerData.set(path, null);
                    }

                    spawnerData.save(spawnerDataFile);
                }

                dirtySpawners.clear();
                deletedSpawners.clear();
            } catch (Exception e) {
                logger.severe("Error during shutdown flush: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isSaving = false;
            }
        }
    }

    private int parseClampedInt(String raw, int min, int max) {
        long value = Long.parseLong(raw);
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return (int) value;
    }

    private long parseClampedLong(String raw, long min, long max) {
        BigInteger value = new BigInteger(raw);
        BigInteger minValue = BigInteger.valueOf(min);
        BigInteger maxValue = BigInteger.valueOf(max);

        if (value.compareTo(minValue) < 0) {
            return min;
        }
        if (value.compareTo(maxValue) > 0) {
            return max;
        }
        return value.longValue();
    }
}
