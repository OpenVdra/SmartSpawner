package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.spawner.data.storage.StorageMode;

/**
 * Every SQL statement the spawner table needs, bound once to a table name and storage backend.
 *
 * <p>{@link #VALUE_COLUMNS} is the single source of the write column order: {@link SpawnerRowMapper}
 * binds them positionally at 2..28 (after {@code spawner_id} at 1), and both upserts are generated
 * from the same array so MySQL and SQLite can never drift.</p>
 *
 * <p>The upsert is chosen from the backend at construction, because the storage mode is fixed for the
 * life of the handler. The dormant {@code rev} column (schema v5) appears in no statement, so an
 * insert defaults it to 0 and an update leaves it untouched.</p>
 */
final class SpawnerSql {

    /** Mutable value columns in the order {@link SpawnerRowMapper#bind} writes them (params 2..28). */
    private static final String[] VALUE_COLUMNS = {
            "world", "loc_x", "loc_y", "loc_z", "chunk_x", "chunk_z",
            "entity_type", "itemspawner_type", "stack_size", "max_stack_size",
            "active", "stop", "activation_range", "delay", "last_spawn_time", "min_mobs", "max_mobs",
            "max_loot_slots", "is_at_capacity", "total_items", "exp", "max_stored_exp",
            "last_interacted_player", "preferred_sort_item", "filtered_items", "storage_items", "config_name"
    };

    /** Read columns, fetched by label in {@link SpawnerRowMapper#fromResultSet}, so order is free. */
    private static final String SELECT_COLUMNS = """
            spawner_id, world, loc_x, loc_y, loc_z,
            entity_type, itemspawner_type, config_name, stack_size, max_stack_size,
            active, stop, activation_range, delay, last_spawn_time, min_mobs, max_mobs,
            max_loot_slots, is_at_capacity, exp, max_stored_exp,
            last_interacted_player, preferred_sort_item, filtered_items, storage_items
            """;

    private static final String UPSERT_MYSQL =
            buildUpsert("ON DUPLICATE KEY UPDATE", "VALUES(%1$s)");
    private static final String UPSERT_SQLITE =
            buildUpsert("ON CONFLICT(spawner_id) DO UPDATE SET", "excluded.%1$s");

    final String selectAll;
    final String selectOne;
    final String selectLocation;
    final String delete;
    final String deleteLocationConflict;
    final String upsert;

    SpawnerSql(String table, StorageMode mode) {
        this.selectAll = "SELECT " + SELECT_COLUMNS + " FROM " + table + ";";
        this.selectOne = "SELECT " + SELECT_COLUMNS + " FROM " + table + " WHERE spawner_id = ?";
        this.selectLocation = "SELECT world, loc_x, loc_y, loc_z FROM " + table + " WHERE spawner_id = ?";
        this.delete = "DELETE FROM " + table + " WHERE spawner_id = ?";
        this.deleteLocationConflict = "DELETE FROM " + table
                + " WHERE world = ? AND loc_x = ? AND loc_y = ? AND loc_z = ? AND spawner_id <> ?";
        this.upsert = (mode == StorageMode.SQLITE ? UPSERT_SQLITE : UPSERT_MYSQL).formatted(table);
    }

    /**
     * @param updateHeader the backend's conflict clause, e.g. {@code ON DUPLICATE KEY UPDATE}
     * @param assignSource format for the incoming value, e.g. {@code VALUES(%1$s)} on MySQL
     * @return the upsert with a {@code %s} table placeholder left for {@link String#formatted}
     */
    private static String buildUpsert(String updateHeader, String assignSource) {
        String insertColumns = "spawner_id, " + String.join(", ", VALUE_COLUMNS);
        String placeholders = "?" + ", ?".repeat(VALUE_COLUMNS.length);

        StringBuilder assignments = new StringBuilder();
        for (int i = 0; i < VALUE_COLUMNS.length; i++) {
            String col = VALUE_COLUMNS[i];
            assignments.append("    ").append(col).append(" = ").append(assignSource.formatted(col));
            assignments.append(i < VALUE_COLUMNS.length - 1 ? ",\n" : "\n");
        }

        return "INSERT INTO %s (" + insertColumns + ") VALUES (" + placeholders + ")\n"
                + updateHeader + "\n" + assignments;
    }
}
