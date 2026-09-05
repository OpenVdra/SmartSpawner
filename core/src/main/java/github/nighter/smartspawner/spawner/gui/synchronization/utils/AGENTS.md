# spawner/gui/synchronization/utils/

Two stateless helpers for the sync services. No tracking, no events.

## LootPreGenerationHelper

The timing layer between `TimerUpdateService` and `SpawnerLootGenerator`. Two thresholds off the time
remaining until the next spawn:

- `PRE_GENERATION_THRESHOLD` (2s): `preGenerateLoot` builds the next batch **asynchronously** and
  stores it in the spawner's `preGenerated*` fields, so the GUI can show it instantly. Guarded by
  `isPreGenerating` / `hasPreGeneratedLoot` so it runs once.
- `EARLY_SPAWN_THRESHOLD` (1s): `addPreGeneratedLootEarly` commits the pre-generated batch a beat
  early to avoid a flicker when the timer resets. It double-checks the elapsed time under
  `dataLock.tryLock(100ms)` and passes the *scheduled* spawn time so the timer resets accurately.

Both re-check `spawnerActive` / `spawnerStop` at every hop and run the actual loot work on the
spawner's region thread (`Scheduler.runLocationTask`). This is the concrete mechanism behind
"loot generation is driven from `synchronization/`" — do not add a second time-based trigger
elsewhere.

## TimerFormatter

`formatTime(millis)` → `"mm:ss"`, built by hand (no `String.format`) and cached in a bounded
`ConcurrentHashMap` because it is called for every menu viewer every tick. Pure and static;
`clearCache()` exists only for memory pressure.

## Gotchas

- `LootPreGenerationHelper` holds `dataLock` only briefly and never across a `Scheduler` hop — keep that. The `preGenerated*` fields are `volatile` and owned by `lootGenerationLock`; read/write them through the `SpawnerData` accessors, not directly (see `spawner/AGENTS.md`).
- `TimerFormatter`'s cache is capped (~150 entries); do not remove the size guard or a spawner with an unusual delay could grow it without bound.
