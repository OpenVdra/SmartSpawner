package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one {@link SpawnerStorage} backend (SQLite or MySQL/MariaDB).
 *
 * <p>This class owns the write queue and lifecycle only; the moving parts live in collaborators:
 * {@link SpawnerSql} holds the statements, {@link SpawnerRowMapper} maps rows to and from
 * {@link SpawnerData}, and {@link CrossServerSpawnerQueries} answers the list GUI's cross-server
 * reads. Mutations are marked dirty and flushed in a single batch on an async timer, on world save,
 * and on shutdown; see spawner/data/CLAUDE.md ("Writes are batched, always").</p>
 */
public class SpawnerDatabaseHandler implements SpawnerStorage {

    /** Used when {@code database.autosave-interval} is absent or unparseable. */
    private static final String DEFAULT_AUTOSAVE_INTERVAL = "3m";
    private static final long DEFAULT_AUTOSAVE_TICKS = 3L * 60L * 20L;

    /** Floor on the configured interval. Flushing more often than this buys nothing and costs I/O. */
    private static final long MIN_AUTOSAVE_TICKS = 30L * 20L;

    private final SmartSpawner plugin;
    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final String serverName;

    private final SpawnerSql sql;
    private final SpawnerRowMapper rowMapper;
    private final CrossServerSpawnerQueries crossServer;

    private final Set<String> dirtySpawners = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedSpawners = ConcurrentHashMap.newKeySet();
    private volatile boolean isSaving = false;
    private Scheduler.Task saveTask = null;

    /** Location strings for spawners whose world was not loaded at read time (for WorldEventHandler). */
    private final Map<String, String> locationCache = new ConcurrentHashMap<>();

    public SpawnerDatabaseHandler(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseManager = databaseManager;
        this.serverName = databaseManager.getServerName();

        String tableSpawners = databaseManager.getTableSpawners();
        this.sql = new SpawnerSql(tableSpawners, databaseManager.getStorageMode());
        this.rowMapper = new SpawnerRowMapper(plugin);
        this.crossServer = new CrossServerSpawnerQueries(databaseManager, logger, tableSpawners);
    }

    // ============== Lifecycle ==============

    @Override
    public boolean initialize() {
        if (!databaseManager.isActive()) {
            logger.severe("Database manager is not active, cannot initialize SpawnerDatabaseHandler");
            return false;
        }
        startSaveTask();
        return true;
    }

    @Override
    public void reloadSettings() {
        startSaveTask();
    }

    /**
     * (Re)starts the batched save timer from {@code database.autosave-interval}, so a changed interval
     * takes effect on {@code /ss reload} rather than needing a restart like the rest of the section.
     */
    private void startSaveTask() {
        long intervalTicks = Math.max(MIN_AUTOSAVE_TICKS, configuredAutosaveTicks());
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveTask = Scheduler.runTaskTimerAsync(this::flushChanges, intervalTicks, intervalTicks);
    }

    /**
     * Parses {@code database.autosave-interval} directly rather than through
     * {@code plugin.getTimeFromConfig}, whose fallback for an unreadable value is one hour. An hour of
     * unsaved spawner data is the wrong answer to a typo; the shipped default is.
     */
    private long configuredAutosaveTicks() {
        String configured = plugin.getConfig().getString("database.autosave-interval", DEFAULT_AUTOSAVE_INTERVAL);
        long ticks = plugin.getTimeFormatter().parseTimeToTicks(configured, -1L);
        if (ticks > 0) {
            return ticks;
        }
        logger.warning("Could not read database.autosave-interval ('" + configured + "'), using "
                + DEFAULT_AUTOSAVE_INTERVAL + " instead.");
        return DEFAULT_AUTOSAVE_TICKS;
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
                logger.info("Saving " + dirtySpawners.size() + " spawners to database on shutdown...");
                if (!deletedSpawners.isEmpty()) {
                    deleteSpawnerBatch(new HashSet<>(deletedSpawners));
                }
                if (!dirtySpawners.isEmpty()) {
                    saveSpawnerBatch(new HashSet<>(dirtySpawners));
                }
                dirtySpawners.clear();
                deletedSpawners.clear();
                logger.info("Database shutdown save completed.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during database shutdown flush", e);
            } finally {
                isSaving = false;
            }
        }
        locationCache.clear();
    }

    // ============== Dirty tracking ==============

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
            locationCache.remove(spawnerId);
        }
    }

    @Override
    public void queueSpawnerForSaving(String spawnerId) {
        markSpawnerModified(spawnerId);
    }

    // ============== Write path ==============

    @Override
    public void flushChanges() {
        if (dirtySpawners.isEmpty() && deletedSpawners.isEmpty()) {
            return;
        }
        if (isSaving) {
            return;
        }
        isSaving = true;

        Scheduler.runTaskAsync(() -> {
            try {
                // Deletes before updates: a spawner broken and re-placed at the same location within
                // one flush window leaves the old row occupying that location, which would otherwise
                // trip UNIQUE(world, loc_x, loc_y, loc_z) when the new row is inserted.
                if (!deletedSpawners.isEmpty()) {
                    Set<String> toDelete = new HashSet<>(deletedSpawners);
                    deletedSpawners.removeAll(toDelete);
                    deleteSpawnerBatch(toDelete);
                }
                if (!dirtySpawners.isEmpty()) {
                    Set<String> toUpdate = new HashSet<>(dirtySpawners);
                    dirtySpawners.removeAll(toUpdate);
                    saveSpawnerBatch(toUpdate);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during database flush", e);
            } finally {
                isSaving = false;
            }
        });
    }

    /**
     * Flushes the dirty spawners in one JDBC batch under a single transaction. If a bad row aborts the
     * batch, {@link #saveSpawnersIndividually} retries row by row so the offender is isolated and can
     * self-heal a stale location row instead of blocking every other write.
     */
    private void saveSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.upsert)) {
            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
                if (spawner == null) continue;

                if (rowMapper.bind(stmt, spawner)) {
                    stmt.addBatch();
                } else {
                    dirtySpawners.add(spawnerId);
                }
            }

            stmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Spawner batch save failed, retrying row by row", e);
            saveSpawnersIndividually(spawnerIds);
        }
    }

    /**
     * Saves each spawner in its own transaction. Before the upsert, any other row occupying this
     * spawner's location is cleared, healing the case where a spawner was broken and re-placed at the
     * same spot and the stale row's delete has not been applied yet.
     */
    private void saveSpawnersIndividually(Set<String> spawnerIds) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement clearStmt = conn.prepareStatement(sql.deleteLocationConflict);
             PreparedStatement stmt = conn.prepareStatement(sql.upsert)) {
            conn.setAutoCommit(false);

            for (String spawnerId : spawnerIds) {
                SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
                if (spawner == null) continue;

                try {
                    Location loc = spawner.getSpawnerLocation();
                    clearStmt.setString(1, loc.getWorld().getName());
                    clearStmt.setInt(2, loc.getBlockX());
                    clearStmt.setInt(3, loc.getBlockY());
                    clearStmt.setInt(4, loc.getBlockZ());
                    clearStmt.setString(5, spawnerId);
                    clearStmt.executeUpdate();

                    if (rowMapper.bind(stmt, spawner)) {
                        stmt.executeUpdate();
                    } else {
                        dirtySpawners.add(spawnerId);
                    }
                    conn.commit();
                } catch (SQLException rowError) {
                    logger.log(Level.SEVERE, "Failed to save spawner " + spawnerId
                            + ", re-queuing for the next flush", rowError);
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // rollback best-effort; the row is re-queued regardless
                    }
                    dirtySpawners.add(spawnerId);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving spawners individually to database", e);
            dirtySpawners.addAll(spawnerIds);
        }
    }

    private void deleteSpawnerBatch(Set<String> spawnerIds) {
        if (spawnerIds.isEmpty()) return;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.delete)) {
            conn.setAutoCommit(false);
            for (String spawnerId : spawnerIds) {
                stmt.setString(1, spawnerId);
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting spawner batch from database", e);
            deletedSpawners.addAll(spawnerIds);
        }
    }

    // ============== Read path ==============

    @Override
    public Map<String, SpawnerData> loadAllSpawnersRaw() {
        Map<String, SpawnerData> loaded = new HashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.selectAll);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String spawnerId = rs.getString("spawner_id");
                try {
                    SpawnerData spawner = rowMapper.fromResultSet(rs);
                    loaded.put(spawnerId, spawner);
                    // A null spawner means the world is not loaded yet; keep its location so
                    // WorldEventHandler can materialize it on WorldLoadEvent.
                    if (spawner == null) {
                        locationCache.put(spawnerId, rawLocationString(rs));
                    }
                } catch (Exception e) {
                    loaded.put(spawnerId, null);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading spawners from database", e);
        }

        return loaded;
    }

    @Override
    public SpawnerData loadSpecificSpawner(String spawnerId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.selectOne)) {
            stmt.setString(1, spawnerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.fromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading spawner " + spawnerId + " from database", e);
        }
        return null;
    }

    @Override
    public String getRawLocationString(String spawnerId) {
        String cached = locationCache.get(spawnerId);
        if (cached != null) {
            return cached;
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.selectLocation)) {
            stmt.setString(1, spawnerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String location = rawLocationString(rs);
                    locationCache.put(spawnerId, location);
                    return location;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting location for spawner " + spawnerId, e);
        }
        return null;
    }

    private static String rawLocationString(ResultSet rs) throws SQLException {
        return rs.getString("world") + "," + rs.getInt("loc_x") + ","
                + rs.getInt("loc_y") + "," + rs.getInt("loc_z");
    }

    // ============== Cross-server list GUI (delegated) ==============

    /** @return this server's name from config, used to label its own spawners in the list GUI. */
    public String getServerName() {
        return serverName;
    }

    public record WorldSpawnerStats(int total, int totalStacked) {}

    public void getDistinctServerNamesAsync(Consumer<List<String>> callback) {
        crossServer.distinctServerNames(callback);
    }

    public void getWorldsForServerAsync(String targetServer, Consumer<Map<String, WorldSpawnerStats>> callback) {
        crossServer.worldsForServer(targetServer, callback);
    }

    public void getTotalStacksForWorldAsync(String targetServer, String worldName, Consumer<Integer> callback) {
        crossServer.totalStacksForWorld(targetServer, worldName, callback);
    }

    public void getSpawnerCountForServerAsync(String targetServer, Consumer<Integer> callback) {
        crossServer.spawnerCountForServer(targetServer, callback);
    }

    public void getCrossServerSpawnersAsync(String targetServer, String worldName,
                                            Consumer<List<CrossServerSpawnerData>> callback) {
        crossServer.spawners(targetServer, worldName, callback);
    }

    public void getCrossServerSpawnersAsync(String targetServer, String worldName,
                                            String filter, String sort,
                                            Consumer<List<CrossServerSpawnerData>> callback) {
        crossServer.spawners(targetServer, worldName, filter, sort, callback);
    }
}
