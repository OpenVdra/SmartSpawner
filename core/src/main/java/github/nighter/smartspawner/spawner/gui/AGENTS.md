# spawner/gui/

Every inventory GUI a player opens on a spawner, plus the machinery that keeps those GUIs in sync
while the underlying `SpawnerData` changes. The domain object lives in `spawner/` (see
`spawner/AGENTS.md`); this package only reads it and paints Bukkit inventories from it.

## Packages

| Package | What it is |
|---|---|
| `layout/` | Parses `gui_layout` config into `GuiLayout` / `GuiButton`; `GuiButtonInteractionService` runs per-button cooldowns and click sounds. Layout defines which slot holds which action. A layout's optional `texts.yml` overrides the locale's `gui.yml` texts (see `language/AGENTS.md`) |
| `main/` | The main spawner menu (`SpawnerMenuUI`, `SpawnerMenuHolder`, `SpawnerMenuAction`): storage/exp/info buttons and the `{time}` countdown |
| `storage/` | The paged virtual-inventory view. See `storage/AGENTS.md` |
| `sell/` | The sell-confirmation GUI (`SpawnerSellConfirmUI` / `...Holder` / `...Listener`) |
| `stacker/` | Data holders for the stacker GUI (`SpawnerSlot`, `InventoryScanResult`); the click handler lives in `spawner/interactions/stack/` |
| `synchronization/` | Live cross-viewer sync and the timer/loot driver. See `synchronization/AGENTS.md` |

## Every GUI is identified by its holder, never its title

Each GUI backs its inventory with a holder that implements `SpawnerHolder` (`SpawnerMenuHolder`,
`StoragePageHolder`, `FilterConfigHolder`, `SpawnerSellConfirmHolder`). To recover the `SpawnerData`
behind an open inventory, cast `inventory.getHolder(false)` to the holder and read it — **never parse
the title**, which is localized and formatted. Every click/close listener in this package guards on
`instanceof <Holder>` first and returns otherwise. `getHolder(false)` (no snapshot) is used
deliberately; keep it.

## Two ways a GUI gets repainted

1. **The acting player** is repainted immediately, inside the click handler, right after the mutation.
2. **Other viewers** are caught up by `synchronization/`. Main-menu viewers are refreshed by a
   batched 1s task; storage viewers are pushed an update whenever code calls
   `SpawnerGuiViewManager.updateSpawnerMenuViewers(spawner)` after changing the inventory.

External code triggers viewer sync through `updateSpawnerMenuViewers`; do not open or rebuild an
inventory per change from outside — schedule the update and let the synchronization layer coalesce it.

## Folia

Anything that touches an inventory, block or entity must run on that object's region thread. GUIs are
opened and repainted through `Scheduler.runLocationTask` / `runEntityTask`; the synchronization
services already hop threads before painting, and re-validate `isOnline()` and the holder type after
the hop. Never touch a viewer's inventory straight from the periodic task thread.

## Reload

`SpawnerStorageUI` and `SpawnerMenuUI` cache built button `ItemStack`s and hold a cache-eviction task.
Their `reload()` rebuilds the caches and `cleanup()`/`cancelTasks()` cancels the task; both are wired
from the hand-ordered reload chain in `ReloadSubCommand` (GUI layout is reloaded before the UIs that
read it). See the reload section in the root `AGENTS.md`.
