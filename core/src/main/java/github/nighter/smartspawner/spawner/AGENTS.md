# spawner/

Everything about a spawner as a domain object: its state, how players interact with the block, how
loot appears, and how it is sold. GUIs, persistence and config parsing have their own maps in
`gui/`, `data/` and the root `AGENTS.md`.

## Packages

| Package | Responsibility |
|---|---|
| `model/` | The domain model: `SpawnerData`, `VirtualInventory`, `ItemSignature` |
| `data/` | Registry and persistence (`SpawnerManager`, storage backends). See `data/AGENTS.md` |
| `gui/` | Every inventory GUI plus live sync. See `gui/AGENTS.md` |
| `interactions/` | Block-level player actions: `click/`, `place/`, `destroy/`, `stack/`, `type/` |
| `lootgen/` | Producing loot into the virtual inventory, and deciding when to run |
| `sell/` | Turning stored items into currency (`SpawnerSellManager`, `SellResult`) |
| `item/` | `SpawnerItemFactory`, builds the spawner ItemStack players hold |
| `natural/` | Vanilla spawners found in the world (`NaturalSpawnerListener`) |
| `config/` | Parses `spawner_mobs.yml` / `spawner_items.yml`, mob head textures |
| `utils/` | `SpawnerTypeChecker`, `SpawnerLocationLockManager` |

## SpawnerData is the source of truth

One `SpawnerData` per spawner block, held by `SpawnerManager`, keyed by `spawnerId` and indexed by
location and world. Stack size is a field on it, not multiple objects: a stack of 30 is one
`SpawnerData` with `stackSize == 30`.

Three tiers of fields, and mixing them up is the usual bug:

- **base values** (`baseMaxStoredExp`, `baseMinMobs`, `baseMaxMobs`, `baseMaxStoragePages`) come from config and are per-single-spawner
- **calculated values** (`maxStoredExp`, `minMobs`, `maxMobs`, `maxStoragePages`, `maxSpawnerLootSlots`) are the base values scaled by `stackSize`
- **live state** (`spawnerExp`, `virtualInventory`, `lastSpawnTime`, `spawnerActive`)

After anything changes `stackSize` or config, the calculated tier must be recomputed
(`calculateStackBasedValues`, `recalculateAfterConfigReload`). Writing a calculated field directly
without going through the recalculation is how stack-size bugs get introduced.

## Locking

`SpawnerData` uses lock striping, deliberately. Pick the narrowest lock:

| Lock | Guards |
|---|---|
| `inventoryLock` | `virtualInventory` reads/writes |
| `lootGenerationLock` | loot generation and the pre-generated loot fields |
| `dataLock` | metadata: exp, stack size, timing, config-derived values |
| `selling` (AtomicBoolean, CAS) | in-progress sell. Not a lock |

Rules that hold across the package:

- Check `isSelling()` before touching the virtual inventory. `SpawnerLootGenerator.spawnLootToSpawner` returns early on it.
- Prefer `tryLock()` over `lock()`. Loot generation runs on server threads and must never block; if the lock is held it skips the cycle rather than waiting.
- `dataLock` is acquired with a short timeout (50ms) inside loot generation, then released before async work. Do not hold a lock across a `Scheduler` boundary.
- `storageDirty` is set when items move inside the storage GUI and cleared when the GUI closes, which is when the spawner gets queued for saving.

## Loot generation flow

`SpawnerRangeChecker` owns the timer and player-proximity check. `SpawnerGuiViewManager` triggers a
spawn when a timer expires. `SpawnerLootGenerator` does the work; it does not decide when to run, so
do not add time checks inside it.

`Config.get().isApproximateLoot()` plus `approximationThreshold` switch large stacks to a statistical
approximation instead of rolling per-mob. Loot tables come from `EntityLootConfig` / `LootItem`,
loaded by `spawner/config/SpawnerSettingsConfig`, which is why the price manager must initialize
before spawner settings (loot config reads prices).

A `LootItem` holds a **prebuilt template** `ItemStack`, resolved once at config load by
`config/LootEntryParser` + `config/ConfiguredItemParser`. `createItemStack()` clones it, so adding
support for a new item property needs no change to `LootItem` or to loot generation: it is expressed
in the config's `item:` field, which accepts a material name, vanilla `/give` component syntax, or
`nbt:` plus Base64. Only a `durability` *range* is rolled per drop; a single value is baked into the
template.

The shipped `spawner_mobs.yml` uses a free label per entry with an explicit `item:`. That is
only safe because `SpawnerSettingsConfig.load()` passes `path -> path.endsWith(".loot")` as
`YamlMigrator.OwnedSection`. Without it, `addMissingKeys` would add every relabelled default entry
next to the user's existing one and silently double every drop. **Keep that argument** if you touch
the load call. `spawner_items.yml` has no migrator at all, so it only affects fresh installs.

Pre-generated loot (`preGeneratedItems`, `preGeneratedExperience`) exists so the GUI shows the next
batch instantly. It is `volatile` and must only be read or written under `lootGenerationLock`.

## Spawner identity

There are three kinds of spawner item, distinguished by PersistentDataContainer keys, not by
material or display name. `SpawnerTypeChecker` is the only correct way to tell them apart:

- **smart spawner**: no marker key, the default
- **vanilla spawner**: has the `vanilla_spawner` boolean key
- **item spawner**: has the `item_spawner_material` string key, and `SpawnerData.spawnedItemMaterial` is set with `entityType == EntityType.ITEM`

The vanilla cage preview is configured through `SpawnerDisplayConfigurator`. Mob entries use their
`nbt_data` `EntitySnapshot`; Item Spawners use the captured `nbt_data` item, then their first loot
template, then a plain material as fallbacks. Apply it to item metadata, placed blocks, database
restoration and config reloads so the preview does not silently lose entity NBT or item components.

`SpawnerTypeChecker.init(plugin)` must run before any check; it is the first line of
`initializeServices()`.

Any code that branches on spawner kind needs to handle item spawners. They reuse the entity code
paths with `EntityType.ITEM`, so an `EntityType` switch that lacks an `ITEM` case will silently
misbehave rather than fail loudly.

## Interactions

| Package | Entry point | Notes |
|---|---|---|
| `interactions/click/` | `SpawnerClickManager` | Routes right-click to the right GUI, holds a cooldown; has `cleanup()` |
| `interactions/place/` | `SpawnerPlaceListener` | Registers a new `SpawnerData` |
| `interactions/destroy/` | `SpawnerBreakListener`, `SpawnerExplosionListener`, `SpawnerRemovalService` | Break logic is config-heavy (silk touch, required tools, drop chance, sell-and-xp on break); `loadConfig()` on reload |
| `interactions/stack/` | `SpawnerStackHandler` | Merging spawner items into an existing block |
| `interactions/type/` | `SpawnEggHandler` | Changing entity type with a spawn egg |

Every one of these must call the matching `hooks/protections/Check*` before acting. See
`hooks/AGENTS.md`. Skipping the check is a protection bypass, not a style issue.

## Gotchas

- `SpawnerData` holds a `SpawnerHologram`. Hologram work touches the world, so it runs through `Scheduler.runLocationTask` (see `SpawnerManager.removeSpawner`).
- `cachedHasNoLoot` and `accumulatedSellValue` / `sellValueDirty` are caches. Invalidate them when loot config or inventory contents change instead of recomputing everywhere.
- A "ghost spawner" is a `SpawnerData` whose block no longer exists. `SpawnerManager.isGhostSpawner` / `removeGhostSpawner` handle it, and `confirmedGhostSpawners` caches the verdict. Do not re-verify in a loop.
- There is no per-location lock. Break and removal are serialized because they run on the block's region thread (`Scheduler.runLocationTask`); removal also dedupes through `SpawnerRemovalService`'s `pendingRemoval*` sets, and the sell path is guarded by `isSelling()`. Keep every spawner mutation on the location thread or that guarantee breaks.
