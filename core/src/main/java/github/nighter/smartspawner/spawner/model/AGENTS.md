# spawner/model/

The domain model: the in-memory objects that *are* a spawner. No persistence, no Bukkit GUI, no
config parsing lives here — those are `data/`, `gui/` and `config/`. Renamed from `properties/` in
1.9.0.

## Files

| File | Role |
|---|---|
| `SpawnerData` | Aggregate root: one per spawner block. Identity, config-scaled values, lock striping, and the public API the rest of the plugin calls |
| `VirtualInventory` | The stored loot: a count-map (source of truth) plus the frozen per-cell display model |
| `ItemSignature` | Value object that groups equal item stacks; the key type of the count-map |
| `SpawnerSellValue` | Package-private. Running total of the stored loot's sell value |
| `SpawnerStorageOps` | Package-private. Every `inventoryLock`-guarded virtual-inventory mutation |
| `PreGeneratedLoot` | Package-private. The next loot batch, generated ahead of the timer |

## SpawnerData is a facade

`SpawnerData` is huge in reach (~50 files import it, plus persistence and the `api/` module), so its
**public method and Lombok-accessor surface is load-bearing** — treat a signature change as a breaking
change. Internally it stays small by delegating the mechanics to three package-private collaborators,
each holding a back-reference to the spawner and reading its live state through getters:

- `SpawnerSellValue` — `getAccumulatedSellValue`, `recalculateSellValue`, `markSellValueDirty`,
  `isSellValueDirty`. The value is kept in step incrementally (`applyAdded`/`applyRemoved`) and only
  fully recomputed when marked dirty (config reload, entity-type or loot-config change).
- `SpawnerStorageOps` — `addItemsAndUpdateSellValue`, `takeItems`, `takeItemsFromCellRange`,
  `takeItemFromCell`, `removeItemsAndUpdateSellValue`, plus the frozen-order methods
  (`freezeStorageOrder`, `applySortPreference`, `markStorageEmptyNow`, `isStorageOrderFrozen`). Each
  runs under `inventoryLock`, keeps the count-map, sell value and `storageVersion` in step, and blocks
  when `isSelling()`.
- `PreGeneratedLoot` — `storePreGeneratedLoot`, `getAndClear…`, `hasPreGeneratedLoot`,
  `setPreGenerating`, `clearPreGeneratedLoot`. All `synchronized` on the holder.

When you add behaviour, put the mechanics in (or next to) a collaborator and keep the `SpawnerData`
method a thin delegator. Do not make a collaborator public; callers go through `SpawnerData`.

## Invariants

- **The count-map is the only source of truth.** `VirtualInventory.consolidatedItems`
  (`Map<ItemSignature, Long>`) holds the real totals; the GUI's Bukkit inventory and the frozen cells
  are projections. Never read a GUI slot to decide what to remove — go through the take primitives.
- **Every inventory mutation bumps `storageVersion`** (`AtomicLong`) so storage views can poll it and
  redraw only when stale. `SpawnerStorageOps` does this; a raw `virtualInventory` write would not.
- **Lock striping.** The locks live on `SpawnerData` (`inventoryLock`, `lootGenerationLock`,
  `dataLock`); collaborators borrow them via `getInventoryLock()` etc. Pick the narrowest, never hold
  one across a `Scheduler` boundary. Sell is a CAS guard (`selling`), not a lock — check `isSelling()`
  before touching the inventory. See `../AGENTS.md` for the full lock/field-tier rules.
- **Three field tiers** (base config → calculated `× stackSize` → live state) are documented in
  `../AGENTS.md`; after changing `stackSize` or config call `calculateStackBasedValues`.

## VirtualInventory & ItemSignature

`VirtualInventory` carries two models under `orderLock`: the packed count-map, and — while a storage
viewer is present — a frozen per-cell layout (`frozenCells`) so items do not jump between actions. The
frozen model, loot insertion policy and the take/removal primitives are specified in
`STORAGE_REDESIGN.md` at the repo root. On load use `addConsolidatedItem(template, amount)`, never a
per-stack `addItems` loop (see `data/AGENTS.md`, "Item serialization").

`ItemSignature` decides when two stacks are "the same" and is what `SpawnerInventoryCodec` iterates,
so **changing it is a data-format change** — bump the codec's `FORMAT_VERSION` and keep a decode branch.
