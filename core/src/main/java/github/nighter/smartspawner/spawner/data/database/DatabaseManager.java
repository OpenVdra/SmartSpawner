package github.nighter.smartspawner.spawner.data.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.legacy.LegacyInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pool.
 * Supports SQLite (default) and MySQL/MariaDB for spawner data storage.
 */
public class DatabaseManager {
    /** Used when {@code database.table-prefix} is absent or sanitizes to nothing. */
    public static final String DEFAULT_TABLE_PREFIX = "sspawner_";

    // HikariCP tuning, in milliseconds. Not exposed in config.yml: the pool size is the only knob
    // that a server owner has a reason to change, and these defaults suit both backends.
    private static final long CONNECTION_TIMEOUT_MS = 10_000L;
    private static final long MAX_LIFETIME_MS = 1_800_000L;
    private static final long IDLE_TIMEOUT_MS = 600_000L;
    private static final long KEEPALIVE_TIME_MS = 30_000L;

    /** Appended to the prefix for the spawner rows table. */
    private static final String SUFFIX_SPAWNERS = "data";
    /** Appended to the prefix for the plugin-owned schema metadata table. */
    private static final String SUFFIX_META = "schema_meta";
    /**
     * Appended to the prefix for the table holding rows a 1.7.x shared database had for servers
     * other than this one. Fixed, so every server looks in the same place for its share.
     */
    private static final String SUFFIX_LEGACY_SHARED = "legacy_shared_data";

    /** Fixed names used before {@code database.table-prefix} existed (1.7.x and earlier). */
    private static final String LEGACY_TABLE_SPAWNERS = "smart_spawners";
    private static final String LEGACY_TABLE_META = "smartspawner_meta";
    /** Index names that travelled with the legacy tables, dropped once those tables are renamed. */
    private static final String[] LEGACY_INDEXES = {"idx_server", "idx_world", "idx_chunk"};

    private final SmartSpawner plugin;
    private final Logger logger;
    private final StorageMode storageMode;
    private HikariDataSource dataSource;

    // Table and index names, all derived from database.table-prefix
    private final String tablePrefix;
    private final String tableSpawners;
    private final String tableMeta;

    /**
     * Whether spawners from several servers share this database.
     *
     * <p>This only decides the table's <em>name</em>. Either way a table holds exactly one server's
     * spawners, so there is no {@code server_name} column in either mode: the name would be the same
     * value on every row, repeated again inside every index. When several servers share a database
     * each one owns {@code <prefix><server>_data} and the list GUI reads the other tables directly,
     * taking each server's name from its table name.</p>
     */
    private final boolean crossServer;

    /** The table this server uses in each mode. Only one of them is {@link #tableSpawners}. */
    private final String tableSingleServer;
    private final String tableThisServer;

    /** Where rows belonging to other servers wait until those servers upgrade. */
    private final String tableLegacyShared;

    // Configuration values
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String serverName;
    private final String sqliteFile;
    private final int poolSize;

    // MySQL/MariaDB table creation SQL. %1$s is the spawner table, %2$s the configured prefix.
    private static final String CREATE_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS %1$s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                spawner_id VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing).
                -- chunk_x/chunk_z are derived from loc_x/loc_z, indexed for per-chunk lookups
                world VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,
                chunk_x INT NOT NULL DEFAULT 0,
                chunk_z INT NOT NULL DEFAULT 0,

                -- What the spawner spawns. itemspawner_type is only set when entity_type is ITEM
                entity_type VARCHAR(64) NOT NULL,
                itemspawner_type VARCHAR(64) DEFAULT NULL,
                config_name VARCHAR(128) DEFAULT NULL,

                -- Stacking
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,

                -- Spawning behaviour
                active BOOLEAN NOT NULL DEFAULT TRUE,
                stop BOOLEAN NOT NULL DEFAULT TRUE,
                activation_range INT NOT NULL DEFAULT 16,
                delay BIGINT NOT NULL DEFAULT 500,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,

                -- Stored loot. total_items is denormalized from storage_items below
                max_loot_slots INT NOT NULL DEFAULT 45,
                is_at_capacity BOOLEAN NOT NULL DEFAULT FALSE,
                total_items BIGINT NOT NULL DEFAULT 0,

                -- Stored experience
                exp BIGINT NOT NULL DEFAULT 0,
                max_stored_exp BIGINT NOT NULL DEFAULT 1000,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Virtual inventory, see SpawnerInventoryCodec
                storage_items MEDIUMBLOB DEFAULT NULL,

                -- Optimistic-concurrency revision, bumped on every write (schema v5). Detects a
                -- foreign writer (another server, a manual DB edit) surviving across a flush cycle.
                rev BIGINT NOT NULL DEFAULT 0,

                -- Indexes. The table is one server's data, so none of these carry a server name.
                UNIQUE KEY uk_spawner (spawner_id),
                UNIQUE KEY uk_location (world, loc_x, loc_y, loc_z),
                INDEX %2$sidx_world (world),
                INDEX %2$sidx_chunk (world, chunk_x, chunk_z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    // SQLite table creation SQL (slightly different syntax)
    private static final String CREATE_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS %1$s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                spawner_id VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing).
                -- chunk_x/chunk_z are derived from loc_x/loc_z, indexed for per-chunk lookups
                world VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,
                chunk_x INT NOT NULL DEFAULT 0,
                chunk_z INT NOT NULL DEFAULT 0,

                -- What the spawner spawns. itemspawner_type is only set when entity_type is ITEM
                entity_type VARCHAR(64) NOT NULL,
                itemspawner_type VARCHAR(64) DEFAULT NULL,
                config_name VARCHAR(128) DEFAULT NULL,

                -- Stacking
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,

                -- Spawning behaviour
                active BOOLEAN NOT NULL DEFAULT 1,
                stop BOOLEAN NOT NULL DEFAULT 1,
                activation_range INT NOT NULL DEFAULT 16,
                delay BIGINT NOT NULL DEFAULT 500,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,

                -- Stored loot. total_items is denormalized from storage_items below
                max_loot_slots INT NOT NULL DEFAULT 45,
                is_at_capacity BOOLEAN NOT NULL DEFAULT 0,
                total_items BIGINT NOT NULL DEFAULT 0,

                -- Stored experience
                exp BIGINT NOT NULL DEFAULT 0,
                max_stored_exp BIGINT NOT NULL DEFAULT 1000,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Virtual inventory, see SpawnerInventoryCodec
                storage_items BLOB DEFAULT NULL,

                -- Optimistic-concurrency revision, bumped on every write (schema v5)
                rev BIGINT NOT NULL DEFAULT 0,

                -- Unique constraints
                UNIQUE (spawner_id),
                UNIQUE (world, loc_x, loc_y, loc_z)
            )
            """;

    // SQLite index creation (separate statements). SQLite index names are database-scoped, not
    // table-scoped, so these carry the prefix as well.
    private static final String CREATE_INDEX_WORLD_SQLITE =
            "CREATE INDEX IF NOT EXISTS %2$sidx_world ON %1$s (world)";
    private static final String CREATE_INDEX_CHUNK_SQLITE =
            "CREATE INDEX IF NOT EXISTS %2$sidx_chunk ON %1$s (world, chunk_x, chunk_z)";

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int CURRENT_SCHEMA_VERSION = 5;

    /** Rows converted per transaction while rewriting inventories during the v3 migration. */
    private static final int MIGRATION_BATCH_SIZE = 250;

    private static final String CREATE_META_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS %1$s (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String CREATE_META_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS %1$s (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    public DatabaseManager(SmartSpawner plugin, StorageMode storageMode) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageMode = storageMode;

        this.tablePrefix = sanitizeTablePrefix(plugin.getConfig().getString("database.table-prefix", DEFAULT_TABLE_PREFIX));
        this.tableMeta = tablePrefix + SUFFIX_META;

        // Load configuration
        this.host = plugin.getConfig().getString("database.host", "localhost");
        this.port = plugin.getConfig().getInt("database.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "smartspawner");
        this.username = plugin.getConfig().getString("database.username", "root");
        this.password = plugin.getConfig().getString("database.password", "");
        this.serverName = plugin.getConfig().getString("database.server-name", "server1");
        this.sqliteFile = plugin.getConfig().getString("database.sqlite-file", "spawners.db");
        this.poolSize = Math.max(1, plugin.getConfig().getInt("database.pool-size", 10));

        // One shared table per server only makes sense when servers actually share a database.
        this.crossServer = storageMode == StorageMode.MYSQL
                && plugin.getConfig().getBoolean("database.sync-across-servers", false);
        this.tableSingleServer = tablePrefix + SUFFIX_SPAWNERS;
        this.tableThisServer = tablePrefix + sanitizeIdentifier(serverName) + "_" + SUFFIX_SPAWNERS;
        this.tableSpawners = crossServer ? tableThisServer : tableSingleServer;
        this.tableLegacyShared = tablePrefix + SUFFIX_LEGACY_SHARED;
    }

    /**
     * Table names are concatenated straight into SQL rather than bound as JDBC parameters, which
     * SQL does not allow for identifiers, so anything outside {@code [A-Za-z0-9_]} is stripped
     * instead of trusted.
     */
    static String sanitizeTablePrefix(String value) {
        String cleaned = sanitizeIdentifier(value);
        return cleaned.isEmpty() ? DEFAULT_TABLE_PREFIX : cleaned;
    }

    /** @see #sanitizeTablePrefix(String) */
    static String sanitizeIdentifier(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "");
    }

    /** Fills the table name and prefix placeholders in the DDL templates above. */
    private String sql(String template) {
        return String.format(template, tableSpawners, tablePrefix);
    }

    /**
     * Initialize the database connection pool and create tables.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            setupDataSource();
            // Renaming has to happen before anything reads the schema version, because the meta
            // table holding that version is itself one of the renamed tables.
            renameLegacyTables();
            adoptTableForCurrentMode();
            createTables();
            createSchemaMetaTable();
            runSchemaMigrations();
            logger.info("Database connection pool initialized successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection pool", e);
            return false;
        }
    }

    /**
     * Moves the spawner table to the name the current {@code database.sync-across-servers} setting
     * calls for, so toggling the setting keeps the data instead of starting empty.
     *
     * <p>An existing table at the target name is never overwritten. That case means two tables hold
     * spawners and only the owner can say which is right, so the target is used as-is and the other
     * is left untouched with a warning naming both.</p>
     */
    private void adoptTableForCurrentMode() throws SQLException {
        String other = crossServer ? tableSingleServer : tableThisServer;
        if (other.equals(tableSpawners) || !tableExists(other)) {
            return;
        }

        if (tableExists(tableSpawners)) {
            logger.warning("Both " + other + " and " + tableSpawners + " exist. Using " + tableSpawners
                    + " and leaving " + other + " alone. Delete whichever one is stale.");
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + other + " RENAME TO " + tableSpawners);
        }
        logger.info("Renamed database table " + other + " to " + tableSpawners
                + " to match database.sync-across-servers.");
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        if (storageMode == StorageMode.SQLITE) {
            setupSQLiteDataSource(config);
        } else {
            setupMySQLDataSource(config);
        }

        dataSource = new HikariDataSource(config);
    }

    private void setupMySQLDataSource(HikariConfig config) {
        // JDBC URL for MariaDB/MySQL
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("github.nighter.smartspawner.libs.mariadb.Driver");
        config.setUsername(username);
        config.setPassword(password);

        // Pool settings
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setKeepaliveTime(KEEPALIVE_TIME_MS);

        // Performance settings for MySQL/MariaDB
        config.setPoolName("SmartSpawner-HikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    private void setupSQLiteDataSource(HikariConfig config) {
        // Create data folder if it doesn't exist
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Pragmas go in the JDBC URL because the xerial driver builds its SQLiteConfig from the URL
        // query string. WAL lets readers run while the batched flush holds the write lock, and
        // busy_timeout is what stops a concurrent reader from failing outright with SQLITE_BUSY.
        File dbFile = new File(dataFolder, sqliteFile);
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&busy_timeout=5000"
                + "&foreign_keys=true"
                + "&cache_size=-16000"
                + "&temp_store=MEMORY";

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setMaxLifetime(0);  // Disable max lifetime for SQLite
        config.setIdleTimeout(0);  // Disable idle timeout for SQLite
        config.setPoolName("SmartSpawner-SQLite-HikariCP");
    }

    /**
     * Move the fixed pre-prefix table names onto {@code database.table-prefix}. No-op on a fresh
     * install and on databases that were already renamed.
     */
    private void renameLegacyTables() throws SQLException {
        renameTableIfNeeded(LEGACY_TABLE_META, tableMeta);
        if (renameTableIfNeeded(LEGACY_TABLE_SPAWNERS, tableSpawners)) {
            dropLegacyIndexes();
        }
    }

    private boolean renameTableIfNeeded(String from, String to) throws SQLException {
        if (!tableExists(from) || tableExists(to)) {
            return false;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + from + " RENAME TO " + to);
        }
        logger.info("Renamed database table " + from + " to " + to + ".");
        return true;
    }

    /**
     * An index follows its table across a rename, keeping its old name. In SQLite index names are
     * database-scoped, so leaving them would make {@link #createSqliteIndexes()} build a second,
     * redundant set under the prefixed names. MySQL scopes index names per table, where the old
     * names stay valid and cost nothing.
     */
    private void dropLegacyIndexes() {
        if (storageMode != StorageMode.SQLITE) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String index : LEGACY_INDEXES) {
                stmt.execute("DROP INDEX IF EXISTS " + index);
            }
        } catch (SQLException e) {
            // The prefixed indexes are created regardless, so a stale duplicate is not worth failing over.
            logger.warning("Could not drop the pre-prefix indexes, leaving them in place: " + e.getMessage());
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, null)) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (storageMode == StorageMode.SQLITE) {
                stmt.execute(sql(CREATE_TABLE_SQLITE));
            } else {
                stmt.execute(sql(CREATE_TABLE_MYSQL));
            }
        }
    }

    /**
     * SQLite indexes are separate statements, and the chunk index can only be created once the
     * chunk columns exist, so this runs after migrations rather than with the table creation.
     */
    private void createSqliteIndexes() throws SQLException {
        if (storageMode != StorageMode.SQLITE) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql(CREATE_INDEX_WORLD_SQLITE));
            stmt.execute(sql(CREATE_INDEX_CHUNK_SQLITE));
        }
    }

    private void createSchemaMetaTable() throws SQLException {
        String template = storageMode == StorageMode.SQLITE ? CREATE_META_TABLE_SQLITE : CREATE_META_TABLE_MYSQL;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(template, tableMeta));
        }
    }

    private void runSchemaMigrations() throws SQLException {
        Integer currentVersion = getSchemaVersionFromMeta();
        if (currentVersion == null) {
            currentVersion = detectInitialSchemaVersion();
            setSchemaVersion(currentVersion);
            logger.info("Initialized database schema version at v" + currentVersion + ".");
        }

        if (currentVersion > CURRENT_SCHEMA_VERSION) {
            logger.warning("Database schema version v" + currentVersion + " is newer than plugin-supported v" + CURRENT_SCHEMA_VERSION + ".");
            return;
        }

        while (currentVersion < CURRENT_SCHEMA_VERSION) {
            int targetVersion = currentVersion + 1;
            logger.info("Applying database schema migration v" + currentVersion + " -> v" + targetVersion + "...");
            applyMigrationStep(targetVersion);
            setSchemaVersion(targetVersion);
            currentVersion = targetVersion;
            logger.info("Database schema migration completed to v" + currentVersion + ".");
        }

        // After the migration steps, so it works on the current column names, and before the
        // indexes, which no longer mention server_name.
        dropServerNameColumn();
        importFromLegacySharedTable();
        createSqliteIndexes();
    }

    /**
     * Columns carried across when the table is rebuilt. {@code id} is included so row identity
     * survives, and {@code server_name} is deliberately absent.
     */
    private static final String REBUILD_COLUMNS = """
            id, spawner_id, world, loc_x, loc_y, loc_z, chunk_x, chunk_z,
            entity_type, itemspawner_type, stack_size, max_stack_size,
            active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
            max_loot_slots, is_at_capacity, total_items, exp, max_stored_exp,
            last_interacted_player, preferred_sort_item, filtered_items, storage_items, rev
            """;

    /**
     * Removes the 1.7.x {@code server_name} column. A table now holds exactly one server's spawners,
     * identified by its name, so the column was the same value on every row.
     *
     * <p>Done by rebuilding rather than {@code DROP COLUMN}: the column is part of both unique
     * constraints, which SQLite refuses to drop out from under, and a shared 1.7.x database holds
     * other servers' rows that must not end up in this server's table. Those rows are left behind in
     * a renamed table so the other servers can still find them when they upgrade.</p>
     */
    private void dropServerNameColumn() throws SQLException {
        if (!columnExists(tableSpawners, "server_name")) {
            return;
        }

        String rebuilt = tableSpawners + "_rebuild";
        String columns = REBUILD_COLUMNS.replace("\n", " ").trim();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + rebuilt);
        }

        // Create the new shape under a scratch name. On SQLite index names are database-scoped, so
        // the named indexes are only added after the old table (and its indexes) are gone.
        String template = storageMode == StorageMode.SQLITE ? CREATE_TABLE_SQLITE : CREATE_TABLE_MYSQL;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(template, rebuilt, tablePrefix + "rebuild_"));
        }

        long copied;
        long foreign;
        try (Connection conn = getConnection()) {
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO " + rebuilt + " (" + columns + ") SELECT " + columns
                            + " FROM " + tableSpawners + " WHERE server_name = ?")) {
                insert.setString(1, serverName);
                copied = insert.executeUpdate();
            }
            try (PreparedStatement count = conn.prepareStatement(
                    "SELECT COUNT(*) FROM " + tableSpawners + " WHERE server_name <> ?")) {
                count.setString(1, serverName);
                try (ResultSet rs = count.executeQuery()) {
                    foreign = rs.next() ? rs.getLong(1) : 0L;
                }
            }
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            if (foreign > 0) {
                stmt.execute("DROP TABLE IF EXISTS " + tableLegacyShared);
                stmt.execute("ALTER TABLE " + tableSpawners + " RENAME TO " + tableLegacyShared);
                logger.warning("Kept " + foreign + " spawners belonging to other servers in "
                        + tableLegacyShared + ". Each of those servers claims its own when it next "
                        + "starts on this version, and the table is removed once it is empty.");
            } else {
                stmt.execute("DROP TABLE " + tableSpawners);
            }
            stmt.execute("ALTER TABLE " + rebuilt + " RENAME TO " + tableSpawners);
        }

        logger.info("Removed the redundant server_name column, keeping " + copied + " spawners.");
    }

    /**
     * Claims this server's rows from the table the first server to upgrade left behind.
     *
     * <p>A 1.7.x database shared by several servers held them all in one table. The first server to
     * start on 1.8.0 takes its own rows and parks the rest under a name every server looks for, which
     * is here. Each later server imports its share and deletes it, and the last one out drops the
     * table.</p>
     */
    private void importFromLegacySharedTable() throws SQLException {
        if (tableLegacyShared.equals(tableSpawners) || !tableExists(tableLegacyShared)) {
            return;
        }

        String columns = REBUILD_COLUMNS.replace("\n", " ").trim();
        long claimed;
        try (Connection conn = getConnection()) {
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO " + tableSpawners + " (" + columns + ") SELECT " + columns
                            + " FROM " + tableLegacyShared + " WHERE server_name = ?")) {
                insert.setString(1, serverName);
                claimed = insert.executeUpdate();
            }
            if (claimed > 0) {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM " + tableLegacyShared + " WHERE server_name = ?")) {
                    delete.setString(1, serverName);
                    delete.executeUpdate();
                }
            }
        }

        long remaining;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableLegacyShared)) {
            remaining = rs.next() ? rs.getLong(1) : 0L;
        }

        if (remaining == 0) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE " + tableLegacyShared);
            }
        }

        if (claimed > 0) {
            logger.info("Claimed " + claimed + " spawners for " + serverName + " from "
                    + tableLegacyShared + (remaining == 0 ? ", which is now removed." : "."));
        }
    }

    private Integer getSchemaVersionFromMeta() throws SQLException {
        String sql = "SELECT meta_value FROM " + tableMeta + " WHERE meta_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, SCHEMA_VERSION_KEY);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String rawVersion = rs.getString("meta_value");
                try {
                    return Integer.parseInt(rawVersion);
                } catch (NumberFormatException ex) {
                    throw new SQLException("Invalid database schema version value: " + rawVersion, ex);
                }
            }
        }
    }

    private int detectInitialSchemaVersion() throws SQLException {
        if (columnExists(tableSpawners, "rev")) {
            return 5;
        }
        if (columnExists(tableSpawners, "config_name")) {
            return 4;
        }
        if (columnExists(tableSpawners, "storage_items")) {
            return 3;
        }
        return xpColumnsRequireMigration() ? LEGACY_SCHEMA_VERSION : 2;
    }

    private void setSchemaVersion(int version) throws SQLException {
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO " + tableMeta + " (meta_key, meta_value) VALUES (?, ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
                : "INSERT INTO " + tableMeta + " (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, SCHEMA_VERSION_KEY);
            stmt.setString(2, String.valueOf(version));
            stmt.executeUpdate();
        }
    }

    private void applyMigrationStep(int targetVersion) throws SQLException {
        switch (targetVersion) {
            case 2 -> migrateXpColumnsToBigIntIfNeeded();
            case 3 -> migrateToChunkAndItemBlobColumns();
            case 4 -> addColumnIfMissing("config_name", "VARCHAR(128) DEFAULT NULL");
            case 5 -> addColumnIfMissing("rev", "BIGINT NOT NULL DEFAULT 0");
            default -> throw new SQLException("No database migration handler found for schema version: " + targetVersion);
        }
    }

    // ============== Schema v2: XP columns to BIGINT ==============

    private void migrateXpColumnsToBigIntIfNeeded() throws SQLException {
        if (!xpColumnsRequireMigration()) {
            return;
        }

        String backupName = createPreMigrationBackup("bigint");
        logger.info("Created database backup before XP BIGINT migration: " + backupName);

        if (storageMode == StorageMode.SQLITE) {
            migrateSQLiteXpColumnsToBigInt();
        } else {
            migrateMySqlXpColumnsToBigInt();
        }

        logger.info("Successfully migrated XP columns to BIGINT.");
    }

    private boolean xpColumnsRequireMigration() throws SQLException {
        return storageMode == StorageMode.SQLITE
                ? sqliteXpColumnsRequireMigration()
                : mySqlXpColumnsRequireMigration();
    }

    private boolean mySqlXpColumnsRequireMigration() throws SQLException {
        String sql = """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name IN ('spawner_exp', 'max_stored_exp')
                """;

        boolean needsMigration = false;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, database);
            stmt.setString(2, tableSpawners);
            try (ResultSet rs = stmt.executeQuery()) {
                int seen = 0;
                while (rs.next()) {
                    seen++;
                    String type = rs.getString("data_type");
                    if (type == null || !"bigint".equalsIgnoreCase(type)) {
                        needsMigration = true;
                    }
                }
                if (seen < 2) {
                    needsMigration = true;
                }
            }
        }
        return needsMigration;
    }

    private boolean sqliteXpColumnsRequireMigration() throws SQLException {
        String sql = "PRAGMA table_info(" + tableSpawners + ")";
        boolean spawnerExpBigInt = false;
        boolean maxStoredExpBigInt = false;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean isBigInt = type != null && type.equalsIgnoreCase("BIGINT");

                if ("spawner_exp".equalsIgnoreCase(name)) {
                    spawnerExpBigInt = isBigInt;
                } else if ("max_stored_exp".equalsIgnoreCase(name)) {
                    maxStoredExpBigInt = isBigInt;
                }
            }
        }

        return !(spawnerExpBigInt && maxStoredExpBigInt);
    }

    private String createPreMigrationBackup(String label) throws SQLException {
        String backupTableName = tableSpawners + "_backup_" + label + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if (storageMode == StorageMode.SQLITE) {
            String backupSql = "CREATE TABLE " + backupTableName + " AS SELECT * FROM " + tableSpawners;
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(backupSql);
            }
        } else {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE " + backupTableName + " LIKE " + tableSpawners);
                stmt.execute("INSERT INTO " + backupTableName + " SELECT * FROM " + tableSpawners);
            }
        }

        return backupTableName;
    }

    private void migrateMySqlXpColumnsToBigInt() throws SQLException {
        String alterSql = """
                ALTER TABLE %1$s
                    MODIFY COLUMN spawner_exp BIGINT NOT NULL DEFAULT 0,
                    MODIFY COLUMN max_stored_exp BIGINT NOT NULL DEFAULT 1000
                """.formatted(tableSpawners);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        }
    }

    private void migrateSQLiteXpColumnsToBigInt() throws SQLException {
        // Recreates the table in its v2 shape. The v3 step below then adds the chunk and item
        // columns on top, so this deliberately keeps the old inventory_data column.
        String scratchTable = tableSpawners + "_pre_bigint";
        String[] migrationSql = {
                "BEGIN IMMEDIATE TRANSACTION",
                "ALTER TABLE " + tableSpawners + " RENAME TO " + scratchTable,
                """
                CREATE TABLE %1$s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    spawner_id VARCHAR(64) NOT NULL,
                    server_name VARCHAR(64) NOT NULL,
                    world_name VARCHAR(128) NOT NULL,
                    loc_x INT NOT NULL,
                    loc_y INT NOT NULL,
                    loc_z INT NOT NULL,
                    entity_type VARCHAR(64) NOT NULL,
                    item_spawner_material VARCHAR(64) DEFAULT NULL,
                    spawner_exp BIGINT NOT NULL DEFAULT 0,
                    spawner_active BOOLEAN NOT NULL DEFAULT 1,
                    spawner_range INT NOT NULL DEFAULT 16,
                    spawner_stop BOOLEAN NOT NULL DEFAULT 1,
                    spawn_delay BIGINT NOT NULL DEFAULT 500,
                    max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                    max_stored_exp BIGINT NOT NULL DEFAULT 1000,
                    min_mobs INT NOT NULL DEFAULT 1,
                    max_mobs INT NOT NULL DEFAULT 4,
                    stack_size INT NOT NULL DEFAULT 1,
                    max_stack_size INT NOT NULL DEFAULT 1000,
                    last_spawn_time BIGINT NOT NULL DEFAULT 0,
                    is_at_capacity BOOLEAN NOT NULL DEFAULT 0,
                    last_interacted_player VARCHAR(64) DEFAULT NULL,
                    preferred_sort_item VARCHAR(64) DEFAULT NULL,
                    filtered_items TEXT DEFAULT NULL,
                    inventory_data TEXT DEFAULT NULL,
                    UNIQUE (server_name, spawner_id),
                    UNIQUE (server_name, world_name, loc_x, loc_y, loc_z)
                )
                """.formatted(tableSpawners),
                """
                INSERT INTO %1$s (
                    id, spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                    entity_type, item_spawner_material, spawner_exp, spawner_active,
                    spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                    max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                    last_spawn_time, is_at_capacity, last_interacted_player,
                    preferred_sort_item, filtered_items, inventory_data
                )
                SELECT
                    id, spawner_id, server_name, world_name, loc_x, loc_y, loc_z,
                    entity_type, item_spawner_material, spawner_exp, spawner_active,
                    spawner_range, spawner_stop, spawn_delay, max_spawner_loot_slots,
                    max_stored_exp, min_mobs, max_mobs, stack_size, max_stack_size,
                    last_spawn_time, is_at_capacity, last_interacted_player,
                    preferred_sort_item, filtered_items, inventory_data
                FROM %2$s
                """.formatted(tableSpawners, scratchTable),
                "DROP TABLE " + scratchTable,
                sql(CREATE_INDEX_WORLD_SQLITE),
                "COMMIT"
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : migrationSql) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            try (Connection rollbackConn = getConnection();
                 Statement rollbackStmt = rollbackConn.createStatement()) {
                rollbackStmt.execute("ROLLBACK");
            } catch (SQLException rollbackEx) {
                logger.log(Level.SEVERE, "Failed to rollback SQLite BIGINT migration", rollbackEx);
            }
            throw e;
        }
    }

    // ============== Schema v3: 1.7.x column names, chunk columns and item blob ==============

    /**
     * Columns 1.7.x wrote, paired with the name 1.8.0 uses. The old names repeated the table's own
     * subject on every column, and {@code spawner_range} was the only one that could not simply be
     * shortened: {@code range} is a reserved word in MySQL 8. {@code entity_type} keeps its 1.7.x
     * name and so is deliberately absent from this table.
     */
    static final String[][] COLUMN_RENAMES = {
            {"world_name", "world"},
            {"item_spawner_material", "itemspawner_type"},
            {"spawner_exp", "exp"},
            {"spawner_active", "active"},
            {"spawner_range", "activation_range"},
            {"spawner_stop", "stop"},
            {"spawn_delay", "delay"},
            {"max_spawner_loot_slots", "max_loot_slots"},
    };

    /**
     * Brings a 1.7.x table up to the 1.8.0 shape: the columns are renamed, the chunk coordinates and
     * the binary inventory columns are added, and every stored inventory is rewritten from the legacy
     * string format into {@link SpawnerInventoryCodec}.
     */
    private void migrateToChunkAndItemBlobColumns() throws SQLException {
        boolean hasLegacyInventory = columnExists(tableSpawners, "inventory_data");

        if (hasLegacyInventory && countRows() > 0) {
            String backupName = createPreMigrationBackup("items");
            logger.info("Created database backup before inventory format migration: " + backupName);
        }

        // First, so everything below can use the current names.
        renameLegacyColumns();

        addColumnIfMissing("chunk_x", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("chunk_z", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("storage_items", storageMode == StorageMode.SQLITE ? "BLOB DEFAULT NULL" : "MEDIUMBLOB DEFAULT NULL");
        addColumnIfMissing("total_items", "BIGINT NOT NULL DEFAULT 0");

        backfillChunkColumns();

        if (hasLegacyInventory) {
            int converted = convertLegacyInventories();
            logger.info("Converted " + converted + " spawner inventories to the binary item format.");
            dropLegacyInventoryColumn();
        }

        if (storageMode != StorageMode.SQLITE) {
            createMySqlChunkIndexIfMissing();
        }
    }

    /**
     * Applies {@link #COLUMN_RENAMES}. Driven by which columns are actually present rather than by
     * the schema version, so a table that is already half converted finishes cleanly, and one that
     * is already current is left alone. Renaming a column carries its indexes with it on both
     * backends, so the unique constraints survive untouched.
     */
    private void renameLegacyColumns() throws SQLException {
        int renamed = 0;
        for (String[] rename : COLUMN_RENAMES) {
            if (!columnExists(tableSpawners, rename[0]) || columnExists(tableSpawners, rename[1])) {
                continue;
            }
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + tableSpawners
                        + " RENAME COLUMN " + rename[0] + " TO " + rename[1]);
            }
            renamed++;
        }
        if (renamed > 0) {
            logger.info("Renamed " + renamed + " database columns to the 1.8.0 names.");
        }
    }

    private long countRows() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableSpawners)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void addColumnIfMissing(String columnName, String definition) throws SQLException {
        if (columnExists(tableSpawners, columnName)) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableSpawners + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    /**
     * Derives chunk coordinates from block coordinates. Done in Java rather than SQL because the
     * arithmetic-shift semantics of {@code >>} on negative values differ between SQLite and MySQL.
     */
    private void backfillChunkColumns() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT id, loc_x, loc_z FROM " + tableSpawners);
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE " + tableSpawners + " SET chunk_x = ?, chunk_z = ? WHERE id = ?")) {

                int pending = 0;
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        update.setInt(1, rs.getInt("loc_x") >> 4);
                        update.setInt(2, rs.getInt("loc_z") >> 4);
                        update.setLong(3, rs.getLong("id"));
                        update.addBatch();

                        if (++pending >= MIGRATION_BATCH_SIZE) {
                            update.executeBatch();
                            pending = 0;
                        }
                    }
                }

                if (pending > 0) {
                    update.executeBatch();
                }
            }

            conn.commit();
        }
    }

    /**
     * Rewrites {@code inventory_data} into the {@code storage_items} blob. Rows whose legacy payload cannot
     * be parsed are left with a null blob and logged, rather than failing the whole migration.
     */
    private int convertLegacyInventories() throws SQLException {
        int converted = 0;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT id, spawner_id, inventory_data FROM " + tableSpawners
                            + " WHERE inventory_data IS NOT NULL AND inventory_data <> ''");
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE " + tableSpawners + " SET storage_items = ?, total_items = ? WHERE id = ?")) {

                int pending = 0;
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        long rowId = rs.getLong("id");
                        String spawnerId = rs.getString("spawner_id");
                        String legacy = rs.getString("inventory_data");

                        byte[] blob;
                        long total;
                        try {
                            Map<ItemStack, Long> items = LegacyInventoryCodec.deserialize(
                                    LegacyInventoryCodec.parseJsonArray(legacy));
                            blob = encodeLegacyItems(items);
                            total = 0L;
                            for (Long amount : items.values()) {
                                total += amount;
                            }
                        } catch (Exception e) {
                            logger.warning("Could not convert stored inventory for spawner " + spawnerId
                                    + ", it will be empty after migration: " + e.getMessage());
                            blob = null;
                            total = 0L;
                        }

                        update.setBytes(1, blob);
                        update.setLong(2, total);
                        update.setLong(3, rowId);
                        update.addBatch();
                        converted++;

                        if (++pending >= MIGRATION_BATCH_SIZE) {
                            update.executeBatch();
                            pending = 0;
                        }
                    }
                }

                if (pending > 0) {
                    update.executeBatch();
                }
            }

            conn.commit();
        }

        return converted;
    }

    /**
     * Encodes legacy item templates. They carry no signature-relevant metadata, so they are wrapped
     * in the same layout {@link SpawnerInventoryCodec} produces by round-tripping through a
     * consolidated map keyed on fresh signatures.
     */
    private byte[] encodeLegacyItems(Map<ItemStack, Long> items) throws Exception {
        if (items.isEmpty()) {
            return null;
        }

        Map<github.nighter.smartspawner.spawner.model.ItemSignature, Long> consolidated =
                new LinkedHashMap<>(Math.max(16, items.size() * 2));
        for (Map.Entry<ItemStack, Long> entry : items.entrySet()) {
            consolidated.merge(
                    new github.nighter.smartspawner.spawner.model.ItemSignature(entry.getKey()),
                    entry.getValue(),
                    Long::sum);
        }

        return SpawnerInventoryCodec.encode(consolidated);
    }

    private void dropLegacyInventoryColumn() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableSpawners + " DROP COLUMN inventory_data");
        } catch (SQLException e) {
            // Harmless if it stays: nothing reads or writes it any more.
            logger.warning("Could not drop the legacy inventory_data column, leaving it in place: " + e.getMessage());
        }
    }

    private void createMySqlChunkIndexIfMissing() throws SQLException {
        List<String> existingIndexes = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, tableSpawners, false, false)) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    if (name != null) {
                        existingIndexes.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        String indexName = tablePrefix + "idx_chunk";
        if (existingIndexes.contains(indexName.toLowerCase(Locale.ROOT))) {
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX " + indexName + " ON " + tableSpawners
                    + " (world, chunk_x, chunk_z)");
        }
    }

    /**
     * Get a connection from the pool.
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Get the configured server name for this server.
     * @return The server name used to identify spawners
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Name of the spawner rows table, {@code database.table-prefix} included.
     * @return The table name to concatenate into SQL
     */
    public String getTableSpawners() {
        return tableSpawners;
    }

    /**
     * Name of the plugin-owned schema metadata table, {@code database.table-prefix} included.
     * @return The table name to concatenate into SQL
     */
    public String getTableMeta() {
        return tableMeta;
    }

    /**
     * The table name used when cross-server mode is off, {@code <prefix>data}.
     * @return the single-server table name, whether or not it is the one in use
     */
    public String getTableSingleServer() {
        return tableSingleServer;
    }

    /**
     * Whether several servers share this database, each in its own table.
     * @return true when {@code database.sync-across-servers} applies
     */
    public boolean isCrossServer() {
        return crossServer;
    }

    /**
     * Every server's spawner table in this database, mapped from server name to table name.
     *
     * <p>Discovered from the table list rather than from a column, because the server name lives in
     * the table name now. Returns just this server when cross-server mode is off.</p>
     */
    public Map<String, String> listServerTables() throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        if (!crossServer) {
            tables.put(serverName, tableSpawners);
            return tables;
        }

        String suffix = "_" + SUFFIX_SPAWNERS;
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, tablePrefix + "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name == null || !name.startsWith(tablePrefix) || !name.endsWith(suffix)) {
                        continue;
                    }
                    String server = name.substring(tablePrefix.length(), name.length() - suffix.length());
                    // Skips <prefix>data, whose "server" would be empty, and the parked legacy table.
                    if (!server.isEmpty()) {
                        tables.put(server, name);
                    }
                }
            }
        }
        tables.putIfAbsent(serverName, tableSpawners);
        return tables;
    }

    /**
     * Get the storage mode this manager is configured for.
     * @return The storage mode (MYSQL or SQLITE)
     */
    public StorageMode getStorageMode() {
        return storageMode;
    }

    /**
     * Check if the database connection pool is active.
     * @return true if the pool is active and accepting connections
     */
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Shutdown the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
