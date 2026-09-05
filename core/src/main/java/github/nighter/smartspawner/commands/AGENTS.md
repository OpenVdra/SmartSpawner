# commands/

Brigadier-based commands, plus the GUIs that are reached from them (as opposed to from a spawner
block, which live in `../spawner/gui/`).

## Registration

Paper's Brigadier API, not `onCommand`. `BrigadierCommandManager` registers on the
`LifecycleEvents.COMMANDS` lifecycle event; `MainCommand` builds one node and registers it three times
so `/smartspawner`, `/spawner` and `/ss` are all the same tree. There is no `commands:` block in
`plugin.yml`.

The root node requires `smartspawner.command.use`, with console and RCON always allowed.

## Adding a subcommand

1. Extend `BaseSubCommand`: implement `getName`, `getPermission`, `getDescription`, `execute`.
2. Add it to the `subCommands` list in the `MainCommand` constructor.
3. Declare the permission in **both** `plugin.yml` and `paper-plugin.yml`.
4. Add the user-facing strings to `command_messages.yml` (and `command_gui.yml` if it opens a GUI) in **every** locale.

`BaseSubCommand.build()` already wraps `execute` with permission checks and audit logging, so do not
re-check the permission or log manually inside `execute`. Return `1` for success, `0` for failure.

`hasPermission` grants console and RCON unconditionally and treats op as sufficient. If a subcommand
must be player-only, check `isPlayer(sender)` inside `execute` and reply with a message key rather than
denying at the `requires` stage, so the command still tab-completes for console.

Arguments are Brigadier arguments added in an overridden `build()`. Look at `SetSubCommand` and
`GiveSubCommand` for the pattern of adding argument nodes while keeping the base wrapper behaviour.

## Current subcommands

| Command | Class | Permission |
|---|---|---|
| `reload` | `reload/ReloadSubCommand` | `smartspawner.command.reload` |
| `give` | `give/GiveSubCommand` | `smartspawner.command.give` |
| `list` | `list/ListSubCommand` | `smartspawner.command.list` |
| `hologram` | `hologram/HologramSubCommand` | `smartspawner.command.hologram` |
| `prices` | `prices/PricesSubCommand` | `smartspawner.command.prices` |
| `clear` | `clear/ClearSubCommand` | `smartspawner.command.clear` |
| `near` | `near/NearSubCommand` | `smartspawner.command.near` |
| `set` | `set/SetSubCommand` | `smartspawner.command.set` |
| `language` | `config/FolderConfigSubCommand` (LANGUAGE) | `smartspawner.command.language` |
| `gui_layout` | `config/FolderConfigSubCommand` (GUI_LAYOUT) | `smartspawner.command.gui_layout` |

`FolderConfigSubCommand` is instantiated twice with different `ConfigOption` values. Adding a third
folder-switching command means a new enum constant, not a new class.

## Reload lives here

`ReloadSubCommand.reloadAll` is the real reload orchestrator for the whole plugin, and
`SmartSpawner.reload()` is only its last step. The ordering in that method is deliberate. See the root
`AGENTS.md` before adding anything to it. `FolderConfigSubCommand` contains a second, shorter reload
path for language and layout switching; a change may belong in both.

## Command GUIs

`list/gui/`, `near/`, `prices/` follow the same holder convention as the spawner GUIs: a `*Holder`
implementing `InventoryHolder`, a `*UI` that builds inventories, a `*Handler` that listens. Identify
the GUI by casting the holder, never by title.

Unlike the spawner GUIs, these are registered from `SmartSpawner.registerListeners()`
(`spawnerListGUI`, `spawnerManagementHandler`, `adminStackerHandler`, `serverSelectionHandler`,
`pricesGUI`, plus `nearResultGUI` and `spawnerHighlightManager` guarded by null checks). They are also
constructed in `setupCommand()`, which runs **after** `initializeComponents()`, so anything they need
must already exist. `MainCommand` reads `plugin.getSpawnerHighlightManager()` at construction time,
which is why `initializeHandlers()` must build it first.

Their text comes from `LanguageManager.commandGui()`, not `gui()`. See `../language/AGENTS.md`.

| Directory | What it is |
|---|---|
| `list/gui/list/` | The paged spawner list, with `enums/SortOption` and `enums/FilterOption`, plus `UserPreferenceCache` for per-player sort/filter |
| `list/gui/worldselection/`, `list/gui/serverselection/` | Scoping the list by world or (cross-server) by server |
| `list/gui/management/` | Per-spawner admin actions |
| `list/gui/adminstacker/` | Admin stack-size editing |
| `near/` | `/ss near`, highlights spawners through walls; `SpawnerHighlightManager` holds per-player sessions and needs `cleanup()` on quit and disable |
| `prices/` | Sell price browser, reads `ItemPriceManager` |
| `hologram/SpawnerHologram` | Not a GUI: the hologram entity attached to a `SpawnerData` |

`ListSubCommand` is 927 lines and is shared by several of those handlers
(`SpawnerManagementHandler` and `ServerSelectionHandler` both take it as a constructor argument). Read
the surrounding region before editing.
