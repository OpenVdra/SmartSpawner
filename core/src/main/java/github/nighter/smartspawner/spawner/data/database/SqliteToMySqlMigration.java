package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.legacy.LegacyInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.model.ItemSignature;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one-time migration from SQLite database to MySQL/MariaDB.
 * After successful migration, the SQLite file is renamed to spawners.db.migrated
 * to prevent re-migration.
 */
public class SqliteToMySqlMigration {
    private final SmartSpawner plugin;
    private final Logger logger;
    private final DatabaseManager mysqlManager;

    private static final String MIGRATED_FILE_SUFFIX = ".migrated";

    // MySQL insert syntax (target)
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

    private static final String LEGACY_TABLE_SPAWNERS = "smart_spawners";
    private static final String TABLE_PLACEHOLDER = "{table}";

    private static final String SELECT_COMMON_COLUMNS = """
            spawner_id, world, loc_x, loc_y, loc_z,
            entity_type, itemspawner_type, stack_size, max_stack_size,
            active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
            max_loot_slots, is_at_capacity, exp, max_stored_exp,
            last_interacted_player, preferred_sort_item, filtered_items
            """;

    /**
     * The same columns under the names 1.7.x used, aliased to the current ones so the row reader
     * below does not have to know which shape it is looking at. A file left behind by 1.7.x is
     * reachable whenever a server upgrades and switches to MySQL in the same step.
     */
    private static final String SELECT_COMMON_COLUMNS_LEGACY = """
            spawner_id, world_name AS world, loc_x, loc_y, loc_z,
            entity_type, item_spawner_material AS itemspawner_type,
            stack_size, max_stack_size,
            spawner_active AS active, spawner_stop AS stop,
            spawner_range AS activation_range, spawn_delay AS delay,
            last_spawn_time, min_mobs, max_mobs,
            max_spawner_loot_slots AS max_loot_slots, is_at_capacity,
            spawner_exp AS exp, max_stored_exp,
            last_interacted_player, preferred_sort_item, filtered_items
            """;

    /** Source file already on the 1.8.0 shape. */
    private static final String SELECT_ALL_SQLITE =
            "SELECT " + SELECT_COMMON_COLUMNS + ", storage_items, total_items, %s FROM " + TABLE_PLACEHOLDER;

    /** Source file left behind by 1.7.x, still on the old names and the string inventory column. */
    private static final String SELECT_ALL_SQLITE_LEGACY =
            "SELECT " + SELECT_COMMON_COLUMNS_LEGACY + ", inventory_data, NULL AS config_name FROM " + TABLE_PLACEHOLDER;

    public SqliteToMySqlMigration(SmartSpawner plugin, DatabaseManager mysqlManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mysqlManager = mysqlManager;
    }

    /**
     * Check if migration is needed.
     * Migration is needed if SQLite database file exists and hasn't been migrated.
     * @return true if migration is needed
     */
    public boolean needsMigration() {
        // Only migrate when target is MySQL
        if (mysqlManager.getStorageMode() != StorageMode.MYSQL) {
            return false;
        }

        String sqliteFileName = plugin.getConfig().getString("database.sqlite-file", "spawners.db");
        File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);

        if (!sqliteFile.exists()) {
            return false;
        }

        // Check if already migrated
        File migratedFile = new File(plugin.getDataFolder(), sqliteFileName + MIGRATED_FILE_SUFFIX);
        if (migratedFile.exists()) {
            return false;
        }

        // Check if SQLite has any data
        return hasSqliteData(sqliteFile);
    }

    private boolean hasSqliteData(File sqliteFile) {
        String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            String table = resolveSourceTable(conn);
            if (table == null) {
                return false;
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            // Table might not exist or other error
        }

        return false;
    }

    /**
     * The leftover SQLite file is never opened by {@link DatabaseManager} in MySQL mode, so it was
     * never brought up to the current schema. It can still carry the pre-v3 table name.
     *
     * @return the spawner table name present in the file, or null when there is none
     */
    private String resolveSourceTable(Connection conn) throws SQLException {
        // SQLite never runs in cross-server mode, so a file written by 1.8.0 uses the single-server
        // name even when MySQL is now on a per-server one.
        for (String candidate : new String[]{
                mysqlManager.getTableSpawners(), mysqlManager.getTableSingleServer(), LEGACY_TABLE_SPAWNERS}) {
            if (sqliteTableExists(conn, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean sqliteTableExists(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean sqliteColumnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Perform the migration from SQLite to MySQL.
     * @return true if migration was successful
     */
    public boolean migrate() {
        logger.info("Starting SQLite to MySQL migration...");

        String sqliteFileName = plugin.getConfig().getString("database.sqlite-file", "spawners.db");
        File sqliteFile = new File(plugin.getDataFolder(), sqliteFileName);

        if (!sqliteFile.exists()) {
            logger.info("No SQLite file found, skipping migration.");
            return true;
        }

        String sqliteJdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

        int totalSpawners = 0;
        int migratedCount = 0;
        int failedCount = 0;

        try (Connection sqliteConn = DriverManager.getConnection(sqliteJdbcUrl)) {

            String sourceTable = resolveSourceTable(sqliteConn);
            if (sourceTable == null) {
                logger.info("SQLite file has no spawner table, skipping migration.");
                return true;
            }

            // A file left behind by an older release still carries the string inventory column.
            boolean hasItemBlob = sqliteColumnExists(sqliteConn, sourceTable, "storage_items");
            boolean hasConfigName = sqliteColumnExists(sqliteConn, sourceTable, "config_name");
            String selectSql = hasItemBlob
                    ? SELECT_ALL_SQLITE.formatted(hasConfigName ? "config_name" : "NULL AS config_name")
                            .replace(TABLE_PLACEHOLDER, sourceTable)
                    : SELECT_ALL_SQLITE_LEGACY.replace(TABLE_PLACEHOLDER, sourceTable);

            try (Connection mysqlConn = mysqlManager.getConnection();
                 PreparedStatement selectStmt = sqliteConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = mysqlConn.prepareStatement(
                         INSERT_SQL_MYSQL.formatted(mysqlManager.getTableSpawners()))) {

            mysqlConn.setAutoCommit(false);

            try (ResultSet rs = selectStmt.executeQuery()) {
                int batchCount = 0;
                final int BATCH_SIZE = 100;

                while (rs.next()) {
                    totalSpawners++;

                    try {
                        int locX = rs.getInt("loc_x");
                        int locZ = rs.getInt("loc_z");

                        // Transfer all columns
                        insertStmt.setString(1, rs.getString("spawner_id"));
                        insertStmt.setString(2, rs.getString("world"));
                        insertStmt.setInt(3, locX);
                        insertStmt.setInt(4, rs.getInt("loc_y"));
                        insertStmt.setInt(5, locZ);
                        insertStmt.setInt(6, locX >> 4);
                        insertStmt.setInt(7, locZ >> 4);
                        insertStmt.setString(8, rs.getString("entity_type"));
                        insertStmt.setString(9, rs.getString("itemspawner_type"));
                        insertStmt.setInt(10, rs.getInt("stack_size"));
                        insertStmt.setInt(11, rs.getInt("max_stack_size"));
                        insertStmt.setBoolean(12, rs.getBoolean("active"));
                        insertStmt.setBoolean(13, rs.getBoolean("stop"));
                        insertStmt.setInt(14, rs.getInt("activation_range"));
                        insertStmt.setLong(15, rs.getLong("delay"));
                        insertStmt.setLong(16, rs.getLong("last_spawn_time"));
                        insertStmt.setInt(17, rs.getInt("min_mobs"));
                        insertStmt.setInt(18, rs.getInt("max_mobs"));
                        insertStmt.setInt(19, rs.getInt("max_loot_slots"));
                        insertStmt.setBoolean(20, rs.getBoolean("is_at_capacity"));

                        if (hasItemBlob) {
                            insertStmt.setLong(21, rs.getLong("total_items"));
                            insertStmt.setBytes(27, rs.getBytes("storage_items"));
                        } else {
                            Map<ItemSignature, Long> items = readLegacyInventory(rs.getString("inventory_data"));
                            insertStmt.setLong(21, SpawnerInventoryCodec.totalItems(items));
                            insertStmt.setBytes(27, SpawnerInventoryCodec.encode(items));
                        }

                        insertStmt.setLong(22, rs.getLong("exp"));
                        insertStmt.setLong(23, rs.getLong("max_stored_exp"));
                        insertStmt.setString(24, rs.getString("last_interacted_player"));
                        insertStmt.setString(25, rs.getString("preferred_sort_item"));
                        insertStmt.setString(26, rs.getString("filtered_items"));
                        insertStmt.setString(28, rs.getString("config_name"));

                        insertStmt.addBatch();
                        batchCount++;
                        migratedCount++;

                        // Execute batch every BATCH_SIZE records
                        if (batchCount >= BATCH_SIZE) {
                            insertStmt.executeBatch();
                            mysqlConn.commit();
                            batchCount = 0;
                            logger.info("Migrated " + migratedCount + " spawners...");
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to migrate spawner: " + rs.getString("spawner_id"), e);
                        failedCount++;
                    }
                }

                // Execute remaining batch
                if (batchCount > 0) {
                    insertStmt.executeBatch();
                    mysqlConn.commit();
                }
            }

            logger.info("Migration completed. Total: " + totalSpawners + ", Migrated: " + migratedCount + ", Failed: " + failedCount);

            // Rename the SQLite file to prevent re-migration
            if (failedCount == 0) {
                File migratedFile = new File(plugin.getDataFolder(), sqliteFileName + MIGRATED_FILE_SUFFIX);
                if (sqliteFile.renameTo(migratedFile)) {
                    logger.info("SQLite file renamed to " + sqliteFileName + MIGRATED_FILE_SUFFIX);
                } else {
                    logger.warning("Failed to rename SQLite file. Manual cleanup may be required.");
                }
            }

            return failedCount == 0;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during SQLite to MySQL migration", e);
            return false;
        }
    }

    private Map<ItemSignature, Long> readLegacyInventory(String inventoryData) {
        if (inventoryData == null || inventoryData.isEmpty()) {
            return Map.of();
        }

        Map<ItemStack, Long> legacyItems = LegacyInventoryCodec.deserialize(
                LegacyInventoryCodec.parseJsonArray(inventoryData));
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
