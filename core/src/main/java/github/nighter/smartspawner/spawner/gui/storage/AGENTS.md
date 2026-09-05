# spawner/gui/storage/

The paged view of a spawner's virtual inventory: 5 rows / 45 item slots per page, one control row.
This is the most invariant-heavy GUI in the plugin because the inventory it shows is virtual — item
counts are `long` and routinely exceed a stack — and because players take items out of it natively.
The full design rationale (Phase A/B, the freeze model, the native-take reconcile) lives in
`STORAGE_REDESIGN.md` at the repo root; this file is the map. The `action/` subpackage has its own
`AGENTS.md`; `filter/` holds the accept-list GUI.

## The count-map is the only source of truth

`VirtualInventory.consolidatedItems` (`Map<ItemSignature, Long>`) holds the real total of every item.
The Bukkit inventory is a **projection** of it. **Never read a GUI slot to decide what to debit.**
Every removal goes through a `SpawnerData` primitive that mutates the count-map under `inventoryLock`
and bumps `storageVersion`; the GUI is repainted afterwards from the count-map, not the other way
round.

## Files

| File | Role |
|---|---|
| `SpawnerStorageUI` | Builds the inventory, buttons, titles; owns the button caches; `updateDisplay` is the one repaint path. `reload()` / `cleanup()` |
| `StoragePageHolder` | `InventoryHolder` + `SpawnerHolder`: carries `SpawnerData`, page state, the per-open `StorageView`, and the `pendingReconcile` flag. Clamps page in its setters |
| `StorageView` | Per-open render cache: last `ItemStack` painted per slot + the `renderedVersion` it reflects. `get()` is public (the reconciler diffs against it); `set()`/`ensureSize()` package-private |
| `StorageRenderer` | Stateless diff painter: `patch()` compares `target[]` against the view and `setItem`s only changed slots — no `player.updateInventory()` |
| `action/` | Every click and the withdraw logic. See `action/AGENTS.md` |
| `filter/` | `FilterConfigHolder` / `FilterConfigUI`: which materials the spawner accepts |

## Frozen per-cell slot model

While a spawner has a storage viewer, the display is **frozen at the cell level**: `VirtualInventory`
holds a `List<FrozenCell>` (each ≤ maxStack; a `null` element is a permanent hole), and that list —
not the packed count-map order — is what renders. This is what stops items sliding up the page after
every take and lets "drop this page" empty exactly this page's cells. Loot merges up and fills holes
(`insertLoot`); take/drop leave holes in place. The cell list and the count-map must always stay in
sync — every mutation path keeps both. See `model/VirtualInventory.java` for the primitives.

Freeze lifecycle is lazy (no task, Folia-friendly):

- **Open:** `createStorageInventory` freezes on the *first* viewer (`!hasStorageViewers`). It re-sorts
  only when the order was never frozen or the grace since the last viewer left has elapsed
  (`STORAGE_ORDER_GRACE_MS`, 3s); otherwise it keeps the old layout so a quick reopen does not make
  items jump.
- **Close:** the last storage viewer leaving calls `spawner.markStorageEmptyNow()` (timestamp only —
  it does **not** unfreeze). Unfreeze happens lazily on the next open past the grace window.
- **Sort:** `applySortPreference` unfreezes, sorts, re-freezes, and bumps the version.

## Two page counts, do not mix them up

| Question | Method | Why |
|---|---|---|
| How many **pages**? | `getVirtualInventory().getDisplaySlotCount()` | The frozen display length (holes included) — matches what is laid out and rendered |
| Is it **at capacity** / how full? | `getVirtualInventory().getUsedSlots()` | The packed count of real item slots |

`SpawnerStorageUI.calculateTotalPages` and `action/StoragePageEditor.calculateTotalPages` **must
return the same value**; both use `getDisplaySlotCount`. They diverged once (UI=display,
Action=packed) and produced a "page 2 shows 2/1, back button stuck" bug. `StoragePageHolder.oldUsedSlots`
is a display-count snapshot, refreshed by `updateOldUsedSlots()`. Any new page-count site uses
`getDisplaySlotCount`; any new capacity check uses `getUsedSlots`.

## Rendering: diff + version

`updateDisplay` is the single repaint path. It snapshots the page under `inventoryLock.tryLock()` —
**skipping the tick if the lock is busy** (the batched task retries; the diff renderer means a skipped
tick just leaves the last, still-correct image) — builds a `target[]` (items + filler + button
overlay), and hands it to `StorageRenderer.patch` with the current `storageVersion`. Only changed
slots are written; there is no `updateInventory()`.

Cross-viewer sync is version-based: every count-map mutation bumps `storageVersion` (`AtomicLong`);
the 1s batched task in `synchronization/` refreshes each storage viewer only when its
`view.renderedVersion` is behind. The acting player is repainted immediately in the handler. A
change that does not bump the version (e.g. `collect_exp`) is not seen by other viewers until the
next loot — accepted.

## Geometry constants

`INVENTORY_SIZE = 54` (item slots 0–44, control row 45–53), `StoragePageHolder.MAX_ITEMS_PER_PAGE = 45`.
`usableItemSlots(spawner, page)` is how many item slots the page's capacity actually reaches; the last
page of a spawner whose capacity is not a multiple of 45 has trailing filler panes
(`RED_STAINED_GLASS_PANE`, display-only). Default installs (capacity = 45 × stack) never hit filler.

## Persistence and reload

Item moves inside the GUI call `markStorageDirty()`; the spawner is queued for saving only on close
(`action/SpawnerStorageAction.handleInventoryClose` → `markSpawnerModified` + `clearStorageDirty`).
`SpawnerStorageUI` caches built button ItemStacks; `reload()` rebuilds them and `cleanup()` cancels
its cache-eviction task. Both are wired from `SmartSpawner.reload()` — see the reload chain in the
parent `gui/AGENTS.md`.

## Gotchas

- `StoragePageHolder` clamps page numbers in its constructor and setters. Do not clamp again at call sites.
- `oldUsedSlots` is a change-detection snapshot; refresh with `updateOldUsedSlots()` after a redraw or the page-count diff misfires.
- Titles are localized. Recover the spawner by casting the holder, never by parsing the title.
