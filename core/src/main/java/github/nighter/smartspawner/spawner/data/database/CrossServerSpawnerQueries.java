package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler.WorldSpawnerStats;
import org.bukkit.entity.EntityType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only aggregate queries for the cross-server list GUI. Each method runs off the main thread and
 * delivers its result back on the main thread through the callback.
 *
 * <p>Since 1.8.0 a table holds exactly one server's spawners and names that server, so selecting a
 * server means selecting a table ({@link #tableFor}) rather than filtering rows. These queries only
 * ever read; writes always target this server's own table in {@link SpawnerDatabaseHandler}.</p>
 */
final class CrossServerSpawnerQueries {

    private static final String SPAWNER_COLUMNS = """
            spawner_id, world, loc_x, loc_y, loc_z,
            entity_type, stack_size, stop, last_interacted_player,
            exp, total_items
            """;

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final String ownTable;

    CrossServerSpawnerQueries(DatabaseManager databaseManager, Logger logger, String ownTable) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.ownTable = ownTable;
    }

    /**
     * The table holding {@code targetServer}'s spawners, or this server's own table when the name is
     * unknown, which is what the single-server case always resolves to anyway.
     */
    private String tableFor(String targetServer) {
        try {
            return databaseManager.listServerTables().getOrDefault(targetServer, ownTable);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not list the spawner tables, using this server's", e);
            return ownTable;
        }
    }

    void distinctServerNames(Consumer<List<String>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<String> servers = new ArrayList<>();
            try {
                servers.addAll(databaseManager.listServerTables().keySet());
                Collections.sort(servers);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error listing the spawner tables in the database", e);
            }
            Scheduler.runTask(() -> callback.accept(servers));
        });
    }

    void worldsForServer(String targetServer, Consumer<Map<String, WorldSpawnerStats>> callback) {
        Scheduler.runTaskAsync(() -> {
            Map<String, WorldSpawnerStats> worlds = new LinkedHashMap<>();
            String sql = "SELECT world, COUNT(*) AS total, COALESCE(SUM(stack_size), 0) AS total_stacked "
                    + "FROM " + tableFor(targetServer) + " GROUP BY world ORDER BY world";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    worlds.put(rs.getString("world"),
                            new WorldSpawnerStats(rs.getInt("total"), rs.getInt("total_stacked")));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching worlds for server " + targetServer, e);
            }
            Scheduler.runTask(() -> callback.accept(worlds));
        });
    }

    void totalStacksForWorld(String targetServer, String worldName, Consumer<Integer> callback) {
        Scheduler.runTaskAsync(() -> {
            int total = 0;
            String sql = "SELECT SUM(stack_size) AS total FROM " + tableFor(targetServer) + " WHERE world = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, worldName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt("total");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching stack total for " + targetServer + "/" + worldName, e);
            }
            int result = total;
            Scheduler.runTask(() -> callback.accept(result));
        });
    }

    void spawnerCountForServer(String targetServer, Consumer<Integer> callback) {
        Scheduler.runTaskAsync(() -> {
            int count = 0;
            String sql = "SELECT COUNT(*) AS count FROM " + tableFor(targetServer);

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("count");
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching spawner count for " + targetServer, e);
            }
            int result = count;
            Scheduler.runTask(() -> callback.accept(result));
        });
    }

    void spawners(String targetServer, String worldName, Consumer<List<CrossServerSpawnerData>> callback) {
        spawners(targetServer, worldName, null, "STACK_SIZE_DESC", callback);
    }

    /**
     * Spawners in one server/world, filtered and sorted for the list GUI.
     *
     * @param filter {@code ACTIVE}, {@code INACTIVE}, or anything else for all
     * @param sort   {@code STACK_SIZE_ASC}, {@code STACK_SIZE_DESC}, or anything else for by-id
     */
    void spawners(String targetServer, String worldName, String filter, String sort,
                  Consumer<List<CrossServerSpawnerData>> callback) {
        Scheduler.runTaskAsync(() -> {
            List<CrossServerSpawnerData> spawners = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT " + SPAWNER_COLUMNS
                    + " FROM " + tableFor(targetServer) + " WHERE world = ?");
            if ("ACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND stop = FALSE");
            } else if ("INACTIVE".equalsIgnoreCase(filter)) {
                sql.append(" AND stop = TRUE");
            }
            if ("STACK_SIZE_ASC".equalsIgnoreCase(sort)) {
                sql.append(" ORDER BY stack_size ASC");
            } else if ("STACK_SIZE_DESC".equalsIgnoreCase(sort)) {
                sql.append(" ORDER BY stack_size DESC");
            } else {
                sql.append(" ORDER BY spawner_id ASC");
            }

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                stmt.setString(1, worldName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        spawners.add(readSpawnerRow(rs, targetServer));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching spawners for " + targetServer + "/" + worldName, e);
            }
            Scheduler.runTask(() -> callback.accept(spawners));
        });
    }

    private CrossServerSpawnerData readSpawnerRow(ResultSet rs, String server) throws SQLException {
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(rs.getString("entity_type"));
        } catch (IllegalArgumentException e) {
            entityType = EntityType.PIG;
        }
        return new CrossServerSpawnerData(
                rs.getString("spawner_id"), server, rs.getString("world"),
                rs.getInt("loc_x"), rs.getInt("loc_y"), rs.getInt("loc_z"),
                entityType, rs.getInt("stack_size"), !rs.getBoolean("stop"),
                rs.getString("last_interacted_player"), rs.getLong("exp"), rs.getLong("total_items"));
    }
}
