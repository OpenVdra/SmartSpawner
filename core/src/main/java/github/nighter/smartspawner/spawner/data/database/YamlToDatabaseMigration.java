package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.legacy.LegacyInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.model.ItemSignature;
import github.nighter.smartspawner.spawner.config.SpawnerConfigName;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one-time migration from spawners_data.yml to database (MySQL or SQLite).
 * After successful migration, the YAML file is renamed to spawners_data.yml.migrated
 * to prevent re-migration.
 */
public class YamlToDatabaseMigration {
    private final SmartSpawner plugin;
    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final String serverName;

    private static final String YAML_FILE_NAME = "spawners_data.yml";
    private static final String MIGRATED_FILE_SUFFIX = ".migrated";

    // MySQL/MariaDB insert syntax
    private static final String INSERT_SQL_MYSQL = """
            INSERT INTO %s (
                spawner_id, world, loc_x, loc_y, loc_z, chunk_x, chunk_z,
                entity_type, itemspawner_type, stack_size, max_stack_size,
                active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
                max_loot_slots, is_at_capacity, total_items, exp, max_stored_exp,
                last_interacted_player, preferred_sort_item, filtered_items, storage_items, config_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                world = VALUES(world),
                loc_x = VALUES(loc_x),
                loc_y = VALUES(loc_y),
                loc_z = VALUES(loc_z),
                chunk_x = VALUES(chunk_x),
                chunk_z = VALUES(chunk_z),
                entity_type = VALUES(entity_type),
                itemspawner_type = VALUES(itemspawner_type),
                stack_size = VALUES(stack_size),
                max_stack_size = VALUES(max_stack_size),
                active = VALUES(active),
                stop = VALUES(stop),
                activation_range = VALUES(activation_range),
                delay = VALUES(delay),
                last_spawn_time = VALUES(last_spawn_time),
                min_mobs = VALUES(min_mobs),
                max_mobs = VALUES(max_mobs),
                max_loot_slots = VALUES(max_loot_slots),
                is_at_capacity = VALUES(is_at_capacity),
                total_items = VALUES(total_items),
                exp = VALUES(exp),
                max_stored_exp = VALUES(max_stored_exp),
                last_interacted_player = VALUES(last_interacted_player),
                preferred_sort_item = VALUES(preferred_sort_item),
                filtered_items = VALUES(filtered_items),
                storage_items = VALUES(storage_items),
                config_name = VALUES(config_name)
            """;

    // SQLite insert syntax
    private static final String INSERT_SQL_SQLITE = """
            INSERT INTO %s (
                spawner_id, world, loc_x, loc_y, loc_z, chunk_x, chunk_z,
                entity_type, itemspawner_type, stack_size, max_stack_size,
                active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
                max_loot_slots, is_at_capacity, total_items, exp, max_stored_exp,
                last_interacted_player, preferred_sort_item, filtered_items, storage_items, config_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(spawner_id) DO UPDATE SET
                world = excluded.world,
                loc_x = excluded.loc_x,
                loc_y = excluded.loc_y,
                loc_z = excluded.loc_z,
                chunk_x = excluded.chunk_x,
                chunk_z = excluded.chunk_z,
                entity_type = excluded.entity_type,
                itemspawner_type = excluded.itemspawner_type,
                stack_size = excluded.stack_size,
                max_stack_size = excluded.max_stack_size,
                active = excluded.active,
                stop = excluded.stop,
                activation_range = excluded.activation_range,
                delay = excluded.delay,
                last_spawn_time = excluded.last_spawn_time,
                min_mobs = excluded.min_mobs,
                max_mobs = excluded.max_mobs,
                max_loot_slots = excluded.max_loot_slots,
                is_at_capacity = excluded.is_at_capacity,
                total_items = excluded.total_items,
                exp = excluded.exp,
                max_stored_exp = excluded.max_stored_exp,
                last_interacted_player = excluded.last_interacted_player,
                preferred_sort_item = excluded.preferred_sort_item,
                filtered_items = excluded.filtered_items,
                storage_items = excluded.storage_items,
                config_name = excluded.config_name
            """;

    public YamlToDatabaseMigration(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseManager = databaseManager;
        this.serverName = databaseManager.getServerName();
    }

    /**
     * Check if migration is needed.
     * Migration is needed if spawners_data.yml exists and has spawner data.
     * @return true if migration is needed
     */
    public boolean needsMigration() {
        File yamlFile = new File(plugin.getDataFolder(), YAML_FILE_NAME);
        if (!yamlFile.exists()) {
            return false;
        }

        // Check if already migrated
        File migratedFile = new File(plugin.getDataFolder(), YAML_FILE_NAME + MIGRATED_FILE_SUFFIX);
        if (migratedFile.exists()) {
            return false;
        }

        // Check if YAML has any spawner data
        FileConfiguration yamlData = YamlConfiguration.loadConfiguration(yamlFile);
        ConfigurationSection spawnersSection = yamlData.getConfigurationSection("spawners");
        return spawnersSection != null && !spawnersSection.getKeys(false).isEmpty();
    }

    /**
     * Perform the migration from YAML to database.
     * @return true if migration was successful
     */
    public boolean migrate() {
        logger.info("Starting YAML to database migration...");

        File yamlFile = new File(plugin.getDataFolder(), YAML_FILE_NAME);
        if (!yamlFile.exists()) {
            logger.info("No YAML file found, skipping migration.");
            return true;
        }

        FileConfiguration yamlData = YamlConfiguration.loadConfiguration(yamlFile);
        ConfigurationSection spawnersSection = yamlData.getConfigurationSection("spawners");

        if (spawnersSection == null || spawnersSection.getKeys(false).isEmpty()) {
            logger.info("No spawners found in YAML file, skipping migration.");
            return true;
        }

        int totalSpawners = spawnersSection.getKeys(false).size();
        int migratedCount = 0;
        int failedCount = 0;

        logger.info("Found " + totalSpawners + " spawners to migrate.");

        // Select appropriate SQL based on storage mode
        String insertSql = (databaseManager.getStorageMode() == StorageMode.SQLITE
                ? INSERT_SQL_SQLITE
                : INSERT_SQL_MYSQL).formatted(databaseManager.getTableSpawners());

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            conn.setAutoCommit(false);
            int batchCount = 0;
            final int BATCH_SIZE = 100;

            for (String spawnerId : spawnersSection.getKeys(false)) {
                try {
                    if (migrateSpawner(stmt, yamlData, spawnerId)) {
                        stmt.addBatch();
                        batchCount++;
                        migratedCount++;

                        // Execute batch every BATCH_SIZE records
                        if (batchCount >= BATCH_SIZE) {
                            stmt.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            logger.info("Migrated " + migratedCount + "/" + totalSpawners + " spawners...");
                        }
                    } else {
                        failedCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to migrate spawner " + spawnerId, e);
                    failedCount++;
                }
            }

            // Execute remaining batch
            if (batchCount > 0) {
                stmt.executeBatch();
                conn.commit();
            }

            logger.info("Migration completed. Migrated: " + migratedCount + ", Failed: " + failedCount);

            // Rename the YAML file to prevent re-migration
            if (failedCount == 0 || migratedCount > 0) {
                File migratedFile = new File(plugin.getDataFolder(), YAML_FILE_NAME + MIGRATED_FILE_SUFFIX);
                if (yamlFile.renameTo(migratedFile)) {
                    logger.info("YAML file renamed to " + YAML_FILE_NAME + MIGRATED_FILE_SUFFIX);
                } else {
                    logger.warning("Failed to rename YAML file. Manual cleanup may be required.");
                }
            }

            return failedCount == 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during migration", e);
            return false;
        }
    }

    private boolean migrateSpawner(PreparedStatement stmt, FileConfiguration yamlData, String spawnerId) throws SQLException {
        String path = "spawners." + spawnerId;

        // Parse location
        String locationString = yamlData.getString(path + ".location");
        if (locationString == null) {
            logger.warning("No location for spawner " + spawnerId + ", skipping.");
            return false;
        }

        String[] locParts = locationString.split(",");
        if (locParts.length != 4) {
            logger.warning("Invalid location format for spawner " + spawnerId + ", skipping.");
            return false;
        }

        String worldName = locParts[0];
        int locX, locY, locZ;
        try {
            locX = Integer.parseInt(locParts[1]);
            locY = Integer.parseInt(locParts[2]);
            locZ = Integer.parseInt(locParts[3]);
        } catch (NumberFormatException e) {
            logger.warning("Invalid location coordinates for spawner " + spawnerId + ", skipping.");
            return false;
        }

        // Parse entity type
        String entityTypeString = yamlData.getString(path + ".entityType");
        if (entityTypeString == null) {
            logger.warning("No entity type for spawner " + spawnerId + ", skipping.");
            return false;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeString);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid entity type for spawner " + spawnerId + ": " + entityTypeString + ", skipping.");
            return false;
        }

        // Parse item spawner material (if applicable)
        String itemSpawnerMaterial = yamlData.getString(path + ".itemSpawnerMaterial");

        // Parse settings string
        String settingsString = yamlData.getString(path + ".settings");
        int spawnerExp = 0;
        boolean spawnerActive = true;
        int spawnerRange = 16;
        boolean spawnerStop = true;
        long spawnDelay = 500;
        int maxSpawnerLootSlots = 45;
        int maxStoredExp = 1000;
        int minMobs = 1;
        int maxMobs = 4;
        int stackSize = 1;
        int maxStackSize = 1000;
        long lastSpawnTime = 0;
        boolean isAtCapacity = false;

        if (settingsString != null) {
            String[] settings = settingsString.split(",");
            int version = yamlData.getInt("data_version", 1);

            try {
                if (version >= 3 && settings.length >= 13) {
                    spawnerExp = Integer.parseInt(settings[0]);
                    spawnerActive = Boolean.parseBoolean(settings[1]);
                    spawnerRange = Integer.parseInt(settings[2]);
                    spawnerStop = Boolean.parseBoolean(settings[3]);
                    spawnDelay = Long.parseLong(settings[4]);
                    maxSpawnerLootSlots = Integer.parseInt(settings[5]);
                    maxStoredExp = Integer.parseInt(settings[6]);
                    minMobs = Integer.parseInt(settings[7]);
                    maxMobs = Integer.parseInt(settings[8]);
                    stackSize = Integer.parseInt(settings[9]);
                    maxStackSize = Integer.parseInt(settings[10]);
                    lastSpawnTime = Long.parseLong(settings[11]);
                    isAtCapacity = Boolean.parseBoolean(settings[12]);
                } else if (settings.length >= 11) {
                    spawnerExp = Integer.parseInt(settings[0]);
                    spawnerActive = Boolean.parseBoolean(settings[1]);
                    spawnerRange = Integer.parseInt(settings[2]);
                    spawnerStop = Boolean.parseBoolean(settings[3]);
                    spawnDelay = Long.parseLong(settings[4]);
                    maxSpawnerLootSlots = Integer.parseInt(settings[5]);
                    maxStoredExp = Integer.parseInt(settings[6]);
                    minMobs = Integer.parseInt(settings[7]);
                    maxMobs = Integer.parseInt(settings[8]);
                    stackSize = Integer.parseInt(settings[9]);
                    lastSpawnTime = Long.parseLong(settings[10]);
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid settings format for spawner " + spawnerId + ", using defaults.");
            }
        }

        // Parse filtered items
        String filteredItemsStr = yamlData.getString(path + ".filteredItems");

        // Parse preferred sort item
        String preferredSortItemStr = yamlData.getString(path + ".preferredSortItem");

        // Parse last interacted player
        String lastInteractedPlayer = yamlData.getString(path + ".lastInteractedPlayer");

        // Read the legacy string inventory and re-encode it into the binary item format
        List<String> inventoryData = yamlData.getStringList(path + ".inventory");
        Map<ItemSignature, Long> items = readLegacyInventory(inventoryData);

        byte[] itemsBlob;
        try {
            itemsBlob = SpawnerInventoryCodec.encode(items);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not encode inventory for spawner " + spawnerId
                    + ", it will be migrated empty", e);
            itemsBlob = null;
            items = Map.of();
        }

        // Set statement parameters
        stmt.setString(1, spawnerId);
        stmt.setString(2, worldName);
        stmt.setInt(3, locX);
        stmt.setInt(4, locY);
        stmt.setInt(5, locZ);
        stmt.setInt(6, locX >> 4);
        stmt.setInt(7, locZ >> 4);
        stmt.setString(8, entityType.name());
        stmt.setString(9, itemSpawnerMaterial);
        stmt.setInt(10, stackSize);
        stmt.setInt(11, maxStackSize);
        stmt.setBoolean(12, spawnerActive);
        stmt.setBoolean(13, spawnerStop);
        stmt.setInt(14, spawnerRange);
        stmt.setLong(15, spawnDelay);
        stmt.setLong(16, lastSpawnTime);
        stmt.setInt(17, minMobs);
        stmt.setInt(18, maxMobs);
        stmt.setInt(19, maxSpawnerLootSlots);
        stmt.setBoolean(20, isAtCapacity);
        stmt.setLong(21, SpawnerInventoryCodec.totalItems(items));
        stmt.setInt(22, spawnerExp);
        stmt.setInt(23, maxStoredExp);
        stmt.setString(24, lastInteractedPlayer);
        stmt.setString(25, preferredSortItemStr);
        stmt.setString(26, filteredItemsStr);
        stmt.setBytes(27, itemsBlob);
        stmt.setString(28, SpawnerConfigName.defaultName(
                itemSpawnerMaterial != null ? itemSpawnerMaterial : entityType.name()));

        return true;
    }

    private Map<ItemSignature, Long> readLegacyInventory(List<String> inventoryData) {
        if (inventoryData == null || inventoryData.isEmpty()) {
            return Map.of();
        }

        Map<ItemStack, Long> legacyItems = LegacyInventoryCodec.deserialize(inventoryData);
        if (legacyItems.isEmpty()) {
            return Map.of();
        }

        Map<ItemSignature, Long> items = new LinkedHashMap<>(Math.max(16, legacyItems.size() * 2));
        for (Map.Entry<ItemStack, Long> entry : legacyItems.entrySet()) {
            items.merge(new ItemSignature(entry.getKey()), entry.getValue(), Long::sum);
        }
        return items;
    }
}
