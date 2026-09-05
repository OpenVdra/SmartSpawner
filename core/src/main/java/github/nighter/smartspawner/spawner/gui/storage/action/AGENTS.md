# spawner/gui/storage/action/

The click surface and withdraw logic for the storage GUI. Six classes, one job each. Only
`SpawnerStorageAction` is a `Listener` and it is the only one registered in `SmartSpawner`
(`plugin.getSpawnerStorageAction()`); every other class here is **package-private** and reached only
through it. The wiring is fixed in the `SpawnerStorageAction` constructor:

```
StoragePageEditor  →  StorageReconciler
                   →  StorageBulkTransfer  →  StorageButtonHandler
```

`StorageBagFiller` is stateless static math with no dependencies.

## Files

| File | Role |
|---|---|
| `SpawnerStorageAction` | The `Listener`. Classifies every click by slot region, enforces take-only / no-deposit, delegates. Holds no business logic |
| `StorageButtonHandler` | Resolves a control button's layout action string and runs it (nav, sort, filter, sell, collect-exp, return; take-all/drop-page delegated) |
| `StorageBulkTransfer` | `take_all` (page → bag) and `drop_page` (page → ground). Transactional, page-scoped |
| `StorageReconciler` | Settles a native item take against the count-map, next tick, using the painted image as baseline |
| `StoragePageEditor` | Shared page math + post-removal bookkeeping so the page rules live in one place |
| `StorageBagFiller` | Pure player-inventory fill and item-projection math (static, no state) |

## Click classification (`SpawnerStorageAction.onInventoryClick`, LOWEST)

The handler no longer cancels everything by default. It branches on `event.getRawSlot()` against
`INVENTORY_SIZE = 54`:

1. `spawner.isSelling()` → cancel + `action_in_progress`. This is the first gate and it stays; it
   closes the sell/reopen dupe window.
2. `raw >= 54` (player's own bag): allow native moves **inside the bag**, but cancel
   `MOVE_TO_OTHER_INVENTORY` (shift-click into storage) and `COLLECT_TO_CURSOR` (double-click gather
   can pull from storage). No reconcile — storage did not change.
3. control slot (`layout.isSlotUsed(raw)`): cancel + `StorageButtonHandler.handleControlSlotClick`.
4. item slot `0..44` and `< usableItemSlots`: if `isTakeOutAction` → let Bukkit run it natively and
   `reconciler.scheduleReconcile`; otherwise (place/swap/hotbar) cancel — **no deposit**. Filler
   slots (≥ `usableItemSlots`) cancel.
5. anything else in the control row: cancel.

`isTakeOutAction` = `PICKUP_*`, `MOVE_TO_OTHER_INVENTORY`, `DROP_ONE_SLOT`, `DROP_ALL_SLOT`,
`COLLECT_TO_CURSOR` — the actions that only ever move items *out* of a slot. `onInventoryDrag` cancels
only when a dragged slot is `< 54` (a drag touching storage is a deposit).

**Native item take-out is only dupe-safe under the single-viewer lock** (enforced in
`synchronization/listeners/InventoryEventListener`). Two Bukkit inventories of one spawner would let
two players pick the same cell in the ~1s window before the refresh. Do not relax the single-viewer
gate without re-thinking this whole path.

## Reconcile — the painted image is the baseline

After a native take, Bukkit has already handed the item to the player but the count-map does not know.
`StorageReconciler` diffs, per page slot, the **last painted image** (`StorageView.get(i)` — what the
player saw and acted on) against the live slot, and debits `painted − matching(now)` from that exact
cell via `spawner.takeItemFromCell(startSlot + i, removed)`. `takeItemFromCell` clamps to the live
amount, so loot that arrived between render and reconcile is kept. Using `painted` (not the current
frozen cells) is deliberate: it is the invariant "what the player took", independent of concurrent
loot.

The debit is scheduled for the **next tick** (`Scheduler.runEntityTaskLater(player, …, 1L)`) because
Bukkit applies the click result only after the LOWEST listener returns. Until it lands, the painted
image is the sole record of the take, and it survives neither a repaint nor a close — so **every path
that repaints, closes, or reads the count-map as authoritative flushes first** via
`flushPendingReconcile`:

- `onInventoryClose` (while the event still carries the storage inventory),
- `onPlayerQuit` (LOWEST backstop, in case close did not fire),
- `StorageButtonHandler.handleControlSlotClick` (before any button action),
- `StorageUpdateService.refreshStorageViewer` (before the version-gated repaint).

`reconcile` disarms `pendingReconcile` *before* it repaints, so the repaint's own re-entrant flush
does not diff against the image it is replacing.

## Withdraw buttons (`StorageBulkTransfer`)

`take_all` and `drop_page` are transactional and scoped to the page being viewed, so they clear in
place instead of pulling items up from later pages:

- **Frozen with no addon listener** → empty the page's exact cells (`takeItemsFromCellRange` for
  drop, `takeFromPageCells` capped by bag space for take-all).
- **Otherwise** (an addon rewrote the item list, or not frozen) → fall back to a by-signature
  `spawner.takeItems(desired)`.

Removal is always "remove from count-map, then place back exactly what was removed" — dupe-safe
against stale views. `simulateBagFill` sizes a take-all before committing so nothing is dropped on the
floor. The `SpawnerTakeAllEvent` / `SpawnerDropAllEvent` API events fire **only here**; native
single-item takes have no event.

## Page bookkeeping (`StoragePageEditor`)

Every withdraw path ends in `updatePageAfterRemoval`: recompute pages from `getDisplaySlotCount`,
clamp the current page, refresh hologram and main-menu viewers, `clearCapacityIfBelow`, and
`markStorageDirty`. `clearCapacityIfBelow` checks `getUsedSlots()` (real packed items), **not** the
display count — capacity is about real occupancy, pages are about layout. `calculateTotalPages` here
must match `SpawnerStorageUI.calculateTotalPages` (both `getDisplaySlotCount`).

## Gotchas

- Never read a GUI slot to decide what to remove. The reconcile debits a `painted − now` delta and then calls a count-map primitive that clamps; that is how "the count-map is truth" holds in the native model.
- `flushPendingReconcile` must run before you read the count-map or repaint. A new code path that does either from outside this package needs to call it (via `plugin.getSpawnerStorageAction()`).
- `handleInventoryClose` is where the spawner is actually queued for saving (`markSpawnerModified` + `clearStorageDirty`), not on each item move.
