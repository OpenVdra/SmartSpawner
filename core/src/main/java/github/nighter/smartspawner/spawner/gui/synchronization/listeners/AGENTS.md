# spawner/gui/synchronization/listeners/

The two Bukkit listeners that feed `ViewerTrackingManager`. Both are registered by
`SpawnerGuiViewManager`'s constructor, not by `SmartSpawner.registerListeners()`.

## InventoryEventListener

Adds and removes viewers, and hosts the single-viewer gate. It handles the same
`InventoryOpenEvent` at two priorities, and order matters:

- `onStorageOpenGate` (`HIGH`, `ignoreCancelled`) runs **first**. If another player already has this
  spawner's storage open (`isStorageViewedByOther`), it cancels the open and sends `storage_in_use`.
  This is the lock that makes native item take-out dupe-safe — at most one Bukkit inventory of a
  spawner's storage exists at a time. A player's own reopen (after a sell or filter round-trip) has
  already closed and untracked the previous storage, so their UUID is absent and the reopen passes.
- `onInventoryOpen` (`MONITOR`, `ignoreCancelled`) tracks the viewer by holder type and calls
  `updateLastInteractedPlayer` immediately (not on close, because close is not guaranteed before quit
  on disconnect), then triggers the task-start hook.

`onInventoryClose` (`MONITOR`) reads the closing holder **before** untracking, so it can detect the
last storage viewer leaving and call `spawner.markStorageEmptyNow()` — the timestamp that starts the
reorder grace window.

The listener takes a `MessageService` in its constructor (passed by `SpawnerGuiViewManager`) purely
to send `storage_in_use`; that key must exist in every locale under
`core/src/main/resources/language/`.

## PlayerEventListener

`onPlayerQuit` (`MONITOR`) untracks the viewer. This exists because `InventoryCloseEvent` is not
guaranteed to fire before `PlayerQuitEvent`, so without it a disconnecting viewer would leak a stale
`Player` reference. `updateLastInteractedPlayer` was already set on open, so nothing else is needed
here.

## Gotchas

- The gate must stay at an earlier priority than the tracking handler and must not depend on the opening player being tracked yet. Keep it keyed on "any other UUID present".
- Only `SpawnerMenuHolder`, `StoragePageHolder`, `FilterConfigHolder` are recognized (`validHolderTypes`). A new spawner GUI needs its holder added here or it is never tracked or synced.
