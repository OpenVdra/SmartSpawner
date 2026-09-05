# spawner/gui/synchronization/

Keeps every open spawner GUI in step with a spawner that never stops producing loot, and is where
loot generation is actually driven from. `SpawnerGuiViewManager` is a thin facade; the work is split
across `managers/`, `services/` and `listeners/`, each with its own `AGENTS.md`.

## Self-registering facade

`SpawnerGuiViewManager` **registers its two listeners in its own constructor**, which is why
`SmartSpawner.registerListeners()` deliberately skips it — registering again would double every open
and close event. `cleanup()` (on disable) unregisters them and stops the task. Do not add it to
`registerListeners()`; do add new listeners here to `cleanup()`.

```
SpawnerGuiViewManager (facade)
├─ managers/    ViewerTrackingManager   who views what, by GUI type
│              UpdateTaskManager        the single 1s repeating task
├─ services/    TimerUpdateService      countdown + drives loot pre-generation
│              GuiUpdateService         batched main-menu redraws
│              StorageUpdateService     version-gated storage repaints
├─ listeners/   InventoryEventListener  add/remove viewers + single-viewer gate
│              PlayerEventListener      remove viewers on quit
└─ utils/       LootPreGenerationHelper, TimerFormatter
```

## One task, started on demand, stopped when empty

`onViewerAdded` starts `UpdateTaskManager`'s repeating task only when a viewer exists;
`processPeriodicUpdates` stops it again when the last viewer leaves. So anything that adds a viewer
outside `InventoryEventListener` must go through `ViewerTrackingManager` and call the start hook, or
the task never runs. Each tick does three things: flush batched main-menu updates
(`GuiUpdateService`), refresh stale storage viewers (`processStorageUpdates`), and run timer updates
if enabled.

## Loot is driven from here

`TimerUpdateService` owns the per-spawner countdown and, through `LootPreGenerationHelper`, is what
pre-generates and adds loot as a timer nears zero. `SpawnerLootGenerator` does the work but does not
decide *when* — do not add a second time-based trigger. (Proximity/activation is
`SpawnerRangeChecker`; see `spawner/AGENTS.md`.)

## Two update models, on purpose

- **Main-menu viewers** are pushed: a mutation calls `updateSpawnerMenuViewers(spawner)`, which
  invalidates the menu cache and schedules a batched `GuiUpdateService` redraw per viewer. This is the
  public entry point for external classes after they change a spawner.
- **Storage viewers** are pulled: a mutation only bumps `storageVersion`; `processStorageUpdates`
  repaints a storage viewer next tick only if its cached image is behind. The acting player is
  repainted immediately in the storage action handler, so this path covers loot and other viewers.
  The storage branch was removed from `updateSpawnerMenuViewers` — do not push storage repaints from
  there.

## Closing viewers safely

`closeAllViewersInventory` closes both storage **and** filter GUI viewers of a spawner (each on its
own region thread via `Scheduler.runEntityTask`). Filter viewers are included to prevent a
duplication exploit where a spawner is removed while its filter GUI is open. Anything that destroys or
replaces a spawner must call this.

## Gotchas

- Register new synchronization listeners in the constructor (like the two existing ones) and unregister them in `cleanup()`. They are not in `SmartSpawner.registerListeners()`.
- Viewers must be dropped on both inventory close and player quit, or the manager holds stale `Player` references. `PlayerEventListener` covers the quit case because `InventoryCloseEvent` is not guaranteed before `PlayerQuitEvent`.
- `recheckTimerPlaceholders()` runs after a language reload so GUI text changes are noticed; it is wired into the reload chain in the parent `gui/AGENTS.md`.
