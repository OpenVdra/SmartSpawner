# spawner/gui/synchronization/services/

The three services the periodic task drives each tick. All rendering runs on the viewer's region
thread via `Scheduler.runLocationTask` — these services are called from the (possibly async) task, so
they must hop to the right thread before touching an inventory.

## GuiUpdateService — batched main-menu redraws

Main-menu updates are coalesced: `scheduleUpdate(uuid, flags)` records a pending player and a bitmask
(`UPDATE_CHEST | UPDATE_INFO | UPDATE_EXP`, or `UPDATE_ALL`); `processPendingUpdates` drains them once
per tick, rebuilds only the flagged buttons, and calls `player.updateInventory()` **only if a slot
actually changed** (`areItemsEqual` compares amount + `isSimilar`). So a mutation marks a redraw and
lets the batch flush it — do not open or rebuild an inventory per change; that fights the batching.

## StorageUpdateService — version-gated storage repaints

`refreshStorageViewer(viewer, spawner)` is called once per storage viewer per tick. On the viewer's
region thread it: (1) flushes any pending native-take reconcile
(`plugin.getSpawnerStorageAction().flushPendingReconcile`), because that debit repaints and bumps the
version itself; (2) gates on `view.renderedVersion >= spawner.storageVersion` and returns early if up
to date; (3) otherwise repaints via `SpawnerStorageUI.updateDisplay`. The gate runs on the region
thread that owns the inventory, so the check is authoritative. Page counts here come from
`getDisplaySlotCount()`, matching the rest of the storage code. `processStorageUpdateDirect` handles
the page-count-changed case (retitle, clamp, or fall back to reopening the inventory).

## TimerUpdateService — countdown + loot driver

Renders the `{time}` countdown on the main-menu info item and, through `LootPreGenerationHelper`, is
what triggers loot pre-generation and early-add as a spawner's timer nears zero (see `utils/`). It
short-circuits entirely when the active GUI layout has no `{time}` placeholder
(`hasTimerPlaceholders`, re-checked on reload by `recheckTimerPlaceholders`). Updates are throttled
(800ms per player), capped (`MAX_PLAYERS_PER_BATCH`), and skipped when the value is unchanged, so a
room full of menu viewers stays cheap. `forceStateChangeUpdate` clears the per-player cache to force
an immediate refresh when spawner state (active/full) flips.

## Gotchas

- Never touch an inventory straight from the task thread. Wrap it in `Scheduler.runLocationTask(viewer.getLocation(), …)` and re-validate `isOnline()` and the holder type inside, exactly as these services do — a viewer can close or move between scheduling and running.
- `StorageUpdateService` must flush the reconcile before its version gate, or a repaint can diff against an image the count-map has not caught up to yet.
- Loot timing lives here, not in `SpawnerLootGenerator`. Do not add a competing trigger.
