# spawner/gui/synchronization/managers/

State-holders for the sync facade. No Bukkit events, no rendering — just tracking and task lifecycle.

## ViewerTrackingManager

Who is looking at which spawner, split by GUI type (`MAIN_MENU`, `STORAGE`, `FILTER`). All maps are
`ConcurrentHashMap` for Folia. It keeps a general player↔spawner map plus three per-type indexes so a
consumer can iterate only the viewers it cares about and detect first/last-viewer transitions:

| Index | Used for |
|---|---|
| `spawnerToMainMenuViewers` / `mainMenuViewers` | timer updates (`TimerUpdateService`) |
| `spawnerToStorageViewersMap` | version refresh, the freeze lifecycle, and the single-viewer gate |
| `spawnerToFilterViewersMap` | force-closing filter GUIs to block a dupe exploit |

`trackViewer` / `untrackViewer` keep every index in step; `untrackViewer` reads the type back off the
stored `ViewerInfo`, so it needs no type argument. Key methods for the storage model:

- `hasStorageViewers(spawner)` — drives the first-viewer freeze decision in `SpawnerStorageUI`.
- `isStorageViewedByOther(spawner, uuid)` — the single-viewer gate. True if any **other** UUID has
  this spawner's storage open. It does not depend on whether the opening player is tracked yet, so it
  is correct regardless of listener ordering at open time.
- `getStorageViewerEntries()` — a fresh snapshot list for the batched version refresh, safe to
  iterate while viewers change.

## UpdateTaskManager

Owns the single repeating task (1s interval, 1s initial delay). `startTask` / `stopTask` are
`synchronized` and idempotent via a `volatile isTaskRunning`. It knows nothing about *what* runs — the
facade passes the runnable and starts it only when a viewer exists, stops it when none remain.

## Gotchas

- Treat the tracking maps as the authority for "is anyone watching". Do not keep a parallel viewer set elsewhere; it will desync.
- Adding a new GUI type means adding its index here **and** its untrack cleanup, or viewers of that type leak on close.
