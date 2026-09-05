# language/

Every player-visible string. No literal text goes to a player anywhere else in the plugin.

## Two entry points

| Use | Class |
|---|---|
| Sending something to a player or console | `MessageService` |
| Getting a string to build an ItemStack or GUI | `LanguageManager` |

`MessageService.sendMessage(sender, key[, placeholders])` is the one to reach for in listeners and
commands. One call does chat + title + subtitle + action bar + sound, because a single message key can
declare all of them; `sendPlayerSpecificContent` fires whichever are present. Do not send a title or
play a sound separately alongside `sendMessage` or the player gets it twice.

`sendConsoleMessage` strips every colour code form (section signs, `&#RRGGBB`, `&a`) before logging.

A missing key is not silent: it logs a warning and tells the player via the `missing_message_key`
message. Key existence is cached in `keyExistsCache`, and `clearKeyExistsCache()` is called from
`ReloadSubCommand` and `FolderConfigSubCommand`, **not** from `SmartSpawner.reload()`. Any new reload
path has to clear it explicitly or newly added keys stay invisible until restart.

`LanguageManager` is a facade over sections; the flat `getX` methods delegate to them:

| Section | Reached via | Backing file |
|---|---|---|
| `MessageLanguageSection` | `getMessage`, `getTitle`, `getActionBar`, `getSound` | `messages.yml`, `command_messages.yml` |
| `GuiLanguageSection` (two instances) | `gui()` and `commandGui()` | `gui.yml`, `command_gui.yml` |
| `ItemLanguageSection` | `getItemName`, `getItemLore`, `getVanillaItemName` | `items.yml` |
| `FormattingLanguageSection` | `formatNumber`, formatted mob names | `formatting.yml` |
| `HologramLanguageSection` | `getHologramText` | `hologram.yml` |

The two GUI sections are distinct. Spawner GUIs use `gui()`; the command-driven GUIs (`/ss list`,
`/ss prices`) use `commandGui()`. Picking the wrong one gives a missing-key warning even though the
text exists.

## Files and locales

`LanguageFileType` is the authoritative list of files per locale: `messages.yml`, `gui.yml`,
`command_gui.yml`, `formatting.yml`, `items.yml`, `command_messages.yml`, `hologram.yml`.

Locales live in `core/src/main/resources/language/<locale>/`, currently `en_US`, `vi_VN`, `tr_TR`,
plus the layout-specific `en_US_DonutSMP` and `en_US_DonutSMP_v2`. The active one is the `language`
config key, switched at runtime by `/ss language` (`commands/config/FolderConfigSubCommand`).

**A new key must be added to every locale directory.** There is no compile-time check and no test;
a key present only in `en_US` produces a runtime warning for everyone else. `en_US` is the reference.

`LanguageRepository` owns loading and exposes `current()` as a `LocaleData` record. `isActive(fileType)`
reports whether that file is loaded, and sections check it before reading, so an absent optional file
degrades instead of throwing.

## Caching

`LanguageCache` is an LRU (`cache/LRUCache`) shared by `PlaceholderFormatter` and
`SmallCapsFormatter`, with hit/miss counters. GUI sections are constructed with explicit cache sizes
(1000 / 250 / 250 in `createGuiSection`).

Consequences:

- `reloadLanguages()` calls `clearCache()` first, which fans out to the item and both GUI sections. A new cache in this package must be added to `LanguageManager.clearCache()`.
- `getCacheStats()` reports hits and misses. Useful for checking a caching change did what you expected.
- Placeholder substitution results are cached by input, so a placeholder map with a per-player value that varies constantly (a countdown, a coordinate) pollutes the cache. Prefer formatting those outside the cached path.
- `ItemLanguageSection.clearCache()` exists separately for item name/lore caches.

## Formatting

- `format/ColorUtil` handles legacy `&` codes, section signs, and `&#RRGGBB` hex.
- `format/PlaceholderFormatter` does `%placeholder%` substitution. Placeholder maps are `Map<String, String>`; pass `Collections.emptyMap()` rather than a fresh `HashMap` (`MessageService` keeps a shared `EMPTY_PLACEHOLDERS` for this).
- `format/NumberFormatter` produces the compact number strings shown in GUIs. Never `String.valueOf` a number into player-facing text; locales differ.
- `format/SmallCapsFormatter` provides the small-caps styling some layouts use.
- `LanguageComponentFormatter` plus the `Component`-returning methods (`buildItemLoreAsComponents`, `translatableLootLine`) are the Adventure path, used where client-side translation of vanilla item names matters. Prefer these for item lore over raw strings.

## Updating shipped language files

`updates/LanguageUpdater` tops up user files with new keys, and a message the user already has keeps
exactly the components they gave it. `ConfigMigrations.ITEM_DEFAULTS` deliberately only fills in
`<section>.default` keys in `items.yml`, because mob-specific sections omit keys **on purpose** to
inherit from `default`. Do not extend it to fill every section; that would break the inheritance.
See `../updates/AGENTS.md`.
