# SmartSpawner

Paper/Folia plugin. Spawners generate items and experience into a virtual inventory instead of
spawning mobs; players interact through GUIs. Java 25, Gradle Kotlin DSL, ~36k LOC across 225 files.

## Modules

| Module | What it is | Depended on by |
|---|---|---|
| `api/` | Public API for addons: events, DTOs, GUI layout builders. No Bukkit runtime logic. | Third-party plugins, `core` |
| `core/` | The plugin itself. Everything else. | nothing |

`core` shades HikariCP, MariaDB driver and bStats (relocated under `github.nighter.smartspawner.libs`).
The SQLite driver is **not** shaded: it extracts a native library from a resource path derived from
its own package name, so relocation breaks it. It is `compileOnly` and comes from the server
classpath at runtime, because Paper bundles `org.xerial:sqlite-jdbc` itself. Do not add it back as a
`libraries:` entry or a `PluginLoader`.
Every protection/shop/economy plugin is `compileOnly` or `implementation`, never required at runtime.

## Build and run

```bash
./gradlew build
```

Produces `core/build/libs/SmartSpawner-<version>.jar` via `shadowJar`. The plain `jar` task outputs
`SmartSpawnerJar-*.jar` and is not the plugin artifact.

```bash
./gradlew runServer
```

Boots a Paper test server in `run/` with the freshly built plugin injected. The Minecraft version is
pinned in `tasks.runServer` in [core/build.gradle.kts](core/build.gradle.kts).

There are **no unit tests** in this repo (0 files under `src/test`). Verification is manual, in game.
See the `playtest-server` skill for the full loop of building, booting and driving the client.

## Architecture in one pass

[SmartSpawner.java](core/src/main/java/github/nighter/smartspawner/SmartSpawner.java) is the wiring
hub: ~50 manager/UI/listener fields, all exposed through Lombok `@Getter`, plus a static `instance`.
Nearly every class takes `SmartSpawner plugin` in its constructor and pulls collaborators off it.
There is no DI container, so **construction order in `onEnable` is load-bearing**:

1. `Config.load` then `IntegrationManager.initializeIntegrations` (protection plugin flags first, everything else branches on them)
2. `migrateDataIfNeeded` (legacy data format conversion)
3. `initializeServices` (language, config updater, logging)
4. `initializeEconomyComponents` (price manager, then spawner settings, because loot config needs prices)
5. `initializeCoreComponents` (storage backend, `SpawnerManager`, GUI layouts, UIs)
6. `initializeHandlers`, `initializeUIAndActions`, hopper, `initializeListeners`
7. `setupCommand`, bStats, `registerListeners`
8. `worldEventHandler.attemptInitialSpawnerLoad()` loads spawners once everything exists

Data flow: a block interaction hits a listener in `spawner/interactions/`, which resolves a
`SpawnerData` from `SpawnerManager`, mutates it, marks it dirty, and a `SpawnerStorage`
implementation batches the write. GUIs read the same `SpawnerData` and are kept in sync by
`SpawnerGuiViewManager`.

## Where to look

| Task | Location |
|---|---|
| Add or change a command | `commands/` (see `commands/AGENTS.md`) |
| Spawner state, stacking, loot, break/place | `spawner/` (see `spawner/AGENTS.md`) |
| Any inventory GUI on a spawner | `spawner/gui/` (see `spawner/gui/AGENTS.md`) |
| Saving and loading spawners, SQLite and MySQL | `spawner/data/` (see `spawner/data/AGENTS.md`) |
| Protection, shop, economy, RPG plugin support | `hooks/` (see `hooks/AGENTS.md`), configured by `sell_integration.yml` |
| Messages, GUI text, number formatting | `language/` (see `language/AGENTS.md`) |
| Renaming a config key without breaking users | `updates/` (see `updates/AGENTS.md`) |
| Hopper pulling from spawner storage | `extras/` |
| Action logging and Discord webhooks | `logging/`, configured by `activity_log.yml` |
| Folia-safe scheduling | [Scheduler.java](core/src/main/java/github/nighter/smartspawner/Scheduler.java) |
| Public API surface for addons | `api/` module |
| Break, particle, natural-spawner and loot-performance config | [Config.java](core/src/main/java/github/nighter/smartspawner/config/Config.java), a parsed snapshot via `Config.get()`. Most other components read `plugin.getConfig()` directly |
| Shipped defaults (config.yml, layouts, language) | `core/src/main/resources/` |
| End-user docs (VitePress) | `docs/` (see the `write-docs` skill) |

## Invariants

**Folia.** `folia-supported: true` in `paper-plugin.yml`. Never call `Bukkit.getScheduler()`
directly. Use `Scheduler.runTask`, `runTaskAsync`, `runTaskLater`, `runLocationTask`,
`runEntityTask`. Anything touching a block, world or entity must run on that object's region thread,
which on Folia means `Scheduler.runLocationTask(location, ...)`. `SpawnerManager.removeSpawner`
is the reference example.

**SpawnerData locking.** `SpawnerData` uses lock striping, not one big lock. Take the right one:
`inventoryLock` for virtual inventory work, `lootGenerationLock` for loot generation and the
pre-generated loot fields, `dataLock` for metadata (exp, stack size). Sell operations are guarded by
an `AtomicBoolean selling` CAS instead of a lock; anything touching the virtual inventory must check
`isSelling()` first.

**Persistence goes through the interface.** Write against
[SpawnerStorage](core/src/main/java/github/nighter/smartspawner/spawner/data/storage/SpawnerStorage.java),
never against `SpawnerDatabaseHandler` directly. After mutating a spawner call
`markSpawnerModified` / `queueSpawnerForSaving`; writes are batched, not immediate. Storage is
always SQL (SQLite by default, MySQL/MariaDB optional); YAML storage was removed in 1.8 and there is
no fallback backend, so a storage failure disables the plugin.

**No config version numbers.** Since 1.7.1 config migration is version-less: `YamlMigrator` plus the
rename tables in `ConfigMigrations`. Do not reintroduce a `config_version` key. Renaming a config key
requires adding a `Rename` entry, otherwise existing servers silently lose the setting.

**Four places to touch when adding a component.** Declaring a field on `SmartSpawner` is not enough:
construct it in the matching `initializeX()` method, register it in `registerListeners()` if it is a
`Listener`, add it to the reload chain (see below) if it holds config or caches, and add it to
`cleanupResources()` if it holds tasks or open GUIs. Re-registering a listener on reload requires
`HandlerList.unregisterAll(old)` first, or events fire twice and the old instance leaks;
`spawnerAuditListener` in `reload()` shows the pattern.

**Reload is orchestrated from the command, not from `reload()`.** `SmartSpawner.reload()` is only the
*last step* of the real sequence in
[ReloadSubCommand.reloadAll](core/src/main/java/github/nighter/smartspawner/commands/reload/ReloadSubCommand.java:43),
which clears caches, re-reads `config.yml` and `Config`, then reloads components in a hand-ordered
dependency chain (hopper, prices, spawner settings, spawner drops, language, GUI layout, menu UI,
click manager, explosion listener, timer placeholders, item factory, holograms) before calling
`plugin.reload()`. Two consequences: putting a reload step only in `SmartSpawner.reload()` runs it
after everything else, and order in that chain is deliberate (GUI layout before the UIs that read it,
item factory after language and loot). `FolderConfigSubCommand` is a second, shorter reload path used
by `/ss language` and `/ss gui_layout`; a change may need to go in both.

**Player-facing strings are never literals.** Everything goes through `MessageService` or
`LanguageManager` with a key, and the key must exist in every locale under
`core/src/main/resources/language/`.

**Permissions are declared, not implicit.** New commands and features need an entry in both
`plugin.yml` and `paper-plugin.yml` under `permissions:`.

## Conventions

- Lombok is used heavily: `@Getter` on classes, `@RequiredArgsConstructor`, `@Setter` on mutable fields. Do not hand-write accessors that Lombok would generate.
- Compilation runs with `-nowarn -Xlint:-deprecation`, so deprecation warnings are invisible. Check Paper API status yourself.
- New Paper API is fine (Brigadier commands, `LifecycleEvents`, `getPluginMeta()`); the target is Paper 1.21.11+.
- Use `plugin.getLogger()` for diagnostics, never `System.out`. Reserve `info` for events an operator needs to see; use `warning`/`severe` for problems.
- One class per file, package layout mirrors feature boundaries. `hooks/` classes are named after the plugin they wrap.
- Files over 600 lines exist (`SpawnerStackerHandler` 1141, `SpawnerDatabaseHandler` 1002, `ListSubCommand` 927, `SpawnerData` 821). Read the surrounding region before editing rather than pattern-matching on a single method.

## Documentation and releases

- `CHANGELOG.md` and the VitePress site in `docs/` are end-user facing and follow a deliberately plain, non-technical style. Use the `write-docs` skill instead of writing them ad hoc.
- Version lives in `allprojects { version = ... }` in the root [build.gradle.kts](build.gradle.kts) and is templated into `plugin.yml` at build time.
- `CONTRIBUTING.md` states the project's priorities: performance first, keep the core small, prefer extending `api/` over growing `core/`.
