# spawner/data/

The in-memory registry of spawners and the SQL persistence layer behind one interface.

## Layout

| File | Role |
|---|---|
| `SpawnerManager` | In-memory registry. The only thing the rest of the plugin should ask for a spawner |
| `WorldEventHandler` | Decides *when* spawners load, keyed off world load/unload events |
| `storage/SpawnerStorage` | The persistence interface. Code outside this package targets this, nothing else |
| `storage/StorageMode` | `SQLITE` (default), `MYSQL`. Use `StorageMode.fromConfig` to read the config value |
| `storage/SpawnerInventoryCodec` | Encodes a virtual inventory into the `items` blob |
| `legacy/LegacyInventoryCodec` | Read-only reader for the removed string item format. Migration only |
| `database/DatabaseManager` | HikariCP pool, schema creation, schema migrations, shared by both modes |
| `database/SpawnerDatabaseHandler` | The one storage backend: write queue + lifecycle, delegates the rest |
| `database/SpawnerSql` | Every statement for the spawner table; generates both upserts from one column list |
| `database/SpawnerRowMapper` | Maps a row to/from `SpawnerData` (`bind` for writes, `fromResultSet` for reads) |
| `database/CrossServerSpawnerQueries` | Read-only aggregate queries for the cross-server list GUI |
| `database/YamlToDatabaseMigration` | `spawners_data.yml` to SQL, one time |
| `database/SqliteToMySqlMigration` | SQLite file to MySQL, one time, MySQL mode only |

## Backend selection

YAML storage was removed in 1.8. `SmartSpawner.initializeStorage()` reads `database.type` and
**has no fallback**: if the pool or the handler fails to come up it returns false and `onEnable`
disables the plugin. Running without persistence would discard every spawner on the next restart,
which is worse than not starting. A config still set to `YAML` resolves to `SQLITE`
(`StorageMode.fromConfig`, plus a value migration in `ConfigMigrations`), and the leftover
`spawners_data.yml` is imported once.

`plugin.getDatabaseManager()` and `plugin.getSpawnerStorage()` are both non-null whenever the plugin
is enabled. Still go through `getSpawnerStorage()` rather than the handler.

Migrations run right after a successful init, gated on `database.migrate-from-local` (default
true) and each migration's own `needsMigration()`.

The whole `database` section is flat and read once in `DatabaseManager`'s constructor, so every key in
it needs a restart. The one exception is `database.autosave-interval`, which `reloadSettings()` picks
up on `/ss reload`. The section was flattened and switched to kebab-case in 1.8.0; the two rename hops
(`database.standalone.*`, then `database.sql.*`) are both in `ConfigMigrations.CONFIG` and must stay
in that order.

## Database schema

Two tables on `database.table-prefix`: `sspawner_data` and `sspawner_schema_meta` (renamed from
`smart_spawners` / `smartspawner_meta` in schema v3). Names live on `DatabaseManager`, never as
literals in queries.

**There is no `server_name` column.** A table holds exactly one server's spawners and says which in
its name, so the column would be one repeated value carried again inside every index. With
`sync-across-servers` on this server owns `<prefix><server>_data`; with it off, `<prefix>data`.
Toggling the setting renames the table (`adoptTableForCurrentMode`), refusing to overwrite an
existing target. Anything that used to filter on `server_name` now picks a *table* instead, through
`SpawnerDatabaseHandler.tableFor(server)` and `DatabaseManager.listServerTables()`, which reads the
server names back off the table names.

Column names were shortened in 1.8.0 (`world_name` to `world`, `spawner_exp` to `exp`, and so on;
see `DatabaseManager.COLUMN_RENAMES`). Three exceptions to the shortening: `spawner_range` became
`activation_range`, not `range`, because `range` is reserved in MySQL 8; `entity_type` keeps its
1.7.x name, so it is absent from `COLUMN_RENAMES`; and `item_spawner_material` became
`itemspawner_type`, which pairs with it. The v1 to v2 migration step deliberately still uses the
1.7.x names, because it reads a 1.7.x table; only the v3 step onwards sees the current ones.

Columns are declared grouped by subject (location, spawner type, stacking, spawning, stored loot,
stored experience, player preferences, timestamps) and the SQL constants mirror that order. The
order is cosmetic to SQL but not to the prepared statements. In `SpawnerSql`, `VALUE_COLUMNS` is the
single source of the write order and generates both upserts; `SpawnerRowMapper.bind` binds those same
columns by position (2..28 after `spawner_id`), and both migrations and `REBUILD_COLUMNS` bind by
position too, so moving a column means moving its bind with it. Only fresh tables and tables rebuilt by
`dropServerNameColumn()` get the physical order; a table migrated by `ALTER TABLE` keeps the old
one and works the same.

`spawner_schema_meta.schema_version` drives `runSchemaMigrations()`. Adding a step means bumping
`CURRENT_SCHEMA_VERSION` and adding a case to `applyMigrationStep`. Two ordering rules:

- Renaming legacy tables happens in `renameLegacyTables()` **before** anything reads the version, because the meta table is itself one of the renamed tables.
- `createTables()` uses `CREATE TABLE IF NOT EXISTS` with the current schema, so it is a no-op for existing databases. Migration steps must bring an old table up to shape themselves.

`chunk_x` / `chunk_z` are written by the handler, indexed as `idx_chunk`. Nothing reads them yet;
they exist for per-chunk spawner loading.

## SpawnerManager

Three indexes, all kept in step by `addSpawner` / `removeSpawner` / `addSpawnerToIndexes`:

- `spawners`: id to `SpawnerData` (`ConcurrentHashMap`)
- `locationIndex`: block-precision `LocationKey` to `SpawnerData` (plain `HashMap`)
- `worldIndex`: world name to set of spawners (plain `HashMap`)

Two of the three are **not** thread safe. Mutate the registry from the main/region thread, and treat
`getAllSpawners()` and `getSpawnersInWorld()` as snapshots to iterate, not as live views to mutate.

`LocationKey` uses `getBlockX/Y/Z`, so sub-block coordinate differences collapse to the same spawner.
Pass the block location; do not construct your own key.

`addSpawner` queues a save automatically. `removeSpawner` does not, so a removal has to be paired with
`markSpawnerDeleted`.

Ghost spawners (a record whose block is gone) are handled by `isGhostSpawner` / `removeGhostSpawner`,
with verdicts cached in `confirmedGhostSpawners`. The `/ss clear` command drives this.

## Loading is world-driven, not startup-driven

`SpawnerManager` is constructed with `initializeWithoutLoading()`. Nothing is loaded until
`WorldEventHandler.attemptInitialSpawnerLoad()` runs at the end of `onEnable`.

A spawner whose world is not loaded yet is kept in `pendingSpawners` and materialized on
`WorldLoadEvent`. This is why `SpawnerStorage.loadAllSpawnersRaw()` is documented to return **null
values** for unloadable spawners and why `getRawLocationString(id)` exists: the handler needs the
world name before it can build a `Location`. Null-check the values of that map.

On `WorldUnloadEvent` the handler **flushes first**, then drops the spawners from the indexes via
`SpawnerManager.unloadSpawnersInWorld`. Order matters: storage handlers resolve dirty spawners back
out of `SpawnerManager`, so unloading before flushing loses the pending writes. They are unloaded,
not deleted.

Because `plugin.yml` declares `load: POSTWORLD`, worlds usually exist by `onEnable`, but
Multiverse-style late world creation is exactly the case this machinery covers.

## Writes are batched, always

Mutating a spawner does not write to disk. The flow is:

```
mutate SpawnerData -> markSpawnerModified(id) / queueSpawnerForSaving(id) -> flushChanges() later
```

`SpawnerDatabaseHandler` keeps `dirtySpawners` and `deletedSpawners` sets and flushes on an async
timer (`startSaveTask()`, `database.autosave-interval`, default 3m, floored at 30s), plus on
`WorldSaveEvent` and on shutdown. `shutdown()` is contractually required to flush before returning, and
`SmartSpawner.saveAndCleanup()` calls `spawnerStorage.shutdown()` before `databaseManager.shutdown()`.
Keep that order: closing the pool first would lose the final flush.

Practical consequences:

- Forgetting to mark a spawner dirty means the change survives until restart and then vanishes. This is the most common bug in this area and there is no test to catch it.
- Do not add a synchronous save on a hot path. Mark and let the batch run.
- Flush work runs async (`Scheduler.runTaskTimerAsync`) and touches `SpawnerData`, so it must take the same locks described in `../AGENTS.md`.
- **The queue holds IDs, not snapshots.** `saveSpawnerBatch` resolves each ID back through `SpawnerManager` and skips it when the lookup returns null. Anything that evicts a spawner from the registry must flush first, the way `WorldEventHandler` does on world unload. Per-chunk eviction cannot be added until this is snapshot-based.

## Data format versioning

`SpawnerData` on disk is versioned by `SmartSpawner.DATA_VERSION` (currently 3) under the
`data_version` key in `spawners_data.yml`. A lower version logs a notice at load and is converted by
`migration/SpawnerDataMigration` + `SpawnerDataConverter`, invoked from `migrateDataIfNeeded()`
**before** components initialize.

This is separate from config migration, which is version-less and lives in `updates/`. Do not
conflate the two: bump `DATA_VERSION` for spawner data shape changes only.

## Item serialization

`SpawnerInventoryCodec` owns the `items` blob. Item templates go through Paper's
`ItemStack.serializeItemsAsBytes`, which is raw NBT and keeps every item component, and each entry
carries a `DataVersion` so items survive a Minecraft version upgrade. Counts are `long` per distinct
item and routinely exceed a stack, so amounts are written as a separate array rather than as
`ItemStack` amounts. `total_items` is denormalized alongside the blob so item totals can be read
without decoding it.

Blob layout is `FORMAT_VERSION` 2: entries are grouped by base item (every component except the
damage value), so a damageable item whose durability *range* rolled many distinct values stores its
base NBT once instead of once per damage. Grouping only normalizes items that actually carry damage;
undamaged and non-damageable items are left untouched, so the common case is byte-for-byte v1 plus
the per-group counters. Layout: `[byte version=2][int groupCount g]` then per group
`[int variantCount v][(int damage, long amount) × v]`, then `[int payloadLen][payload]` where the
payload is `g` base templates each amount 1 and damage 0. The old v1 flat layout
(`[byte version=1][int n][long amount × n][int payloadLen][payload]`, one full template per entry) is
still read by `decode`. Changing the layout means bumping `FORMAT_VERSION` and keeping a decode
branch for every old value.

On load use `VirtualInventory.addConsolidatedItem(template, amount)`, never a loop of
`addItems(singletonList(batch))`: the latter costs one map merge per stack, so a single entry of a
few million items becomes millions of merges.

`ItemSignature` groups equal stacks in `VirtualInventory` and is what the codec iterates. Changing
either is a data format change.
