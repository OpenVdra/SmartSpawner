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

The spawner GUI section (`gui()`, not `commandGui()`) first checks the active layout's
`gui_layouts/<layout>/texts.yml`, then the locale's `gui.yml`. `GuiLayoutConfig.loadLayout()` loads it
through `LanguageManager.loadLayoutTexts`, so both reload paths pick it up. It has the same keys as
`gui.yml`, so a GUI key rename goes in `ConfigMigrations.GUI`, which is applied to both. The
`en_US_DonutSMP*` locales were folded into it by `updates/LayoutTextsMigration`.

The two GUI sections are distinct. Spawner GUIs use `gui()`; the command-driven GUIs (`/ss list`,
`/ss prices`) use `commandGui()`. Picking the wrong one gives a missing-key warning even though the
text exists.

## Files and locales

Only the active locale and locale folders already on disk are extracted and updated
(`LanguageFiles.localesInUse`); `SUPPORTED_LANGUAGES` is the list of bundled ones, used by the
extractor and the `/ss language` tab completion. A custom locale only gets renames, never a top-up.

`LanguageFileType` is the authoritative list of files per locale: `messages.yml`, `gui.yml`,
`command_gui.yml`, `formatting.yml`, `items.yml`, `command_messages.yml`, `hologram.yml`.

Locales live in `core/src/main/resources/language/<locale>/`, currently `en_US`, `vi_VN`, `tr_TR`,
`es_ES`, `zh_CN`. The exact set drifts, so
`ls` that directory rather than trusting this list. The active one is the `language` config key,
switched at runtime by `/ss language` (`commands/config/FolderConfigSubCommand`).

Use the `write-language-files` skill when adding, renaming, translating or commenting keys.

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

- `format/TextFormatter` is the one parser. Every string accepts MiniMessage, legacy `&`/section-sign codes and `&#RRGGBB`, mixed. Legacy codes are rewritten to tags first (a legacy colour after a legacy decoration emits `<reset>`, keeping legacy semantics), `{key}` placeholders become generated tags, and the result is serialised back to a section-sign string with hex, because output is still the Bukkit `String` API. Unknown tags stay literal, so usage lines like `<stack_size|range|delay>` survive; shipped text escapes placeholder-like brackets as `\<value>`. Placeholder values are inserted unparsed, except values carrying legacy codes (already formatted lines), which keep only those codes. `{ᴇɴᴛɪᴛʏ}` and `{entity_small}` alias each other. `<smallcaps>` is a custom tag. Tested in `TextFormatterTest`.
- Shipped language files are MiniMessage; owners' files may still hold `&` codes, which must keep working. Compare owner and shipped values after rendering, never as raw strings.
- `format/ColorUtil.translateHexColorCodes` is a thin wrapper over `TextFormatter.toLegacy`, kept for callers outside this package.
- `format/PlaceholderFormatter` caches `TextFormatter.toLegacy` results by input and placeholders. Despite the old docs, the syntax is `{placeholder}`. Placeholder maps are `Map<String, String>`; pass `Collections.emptyMap()` rather than a fresh `HashMap` (`MessageService` keeps a shared `EMPTY_PLACEHOLDERS` for this).
- `format/NumberFormatter` produces the compact number strings shown in GUIs. Never `String.valueOf` a number into player-facing text; locales differ.
- `format/SmallCapsFormatter` provides the small-caps styling some layouts use.
- `LanguageComponentFormatter` plus the `Component`-returning methods (`buildItemLoreAsComponents`, `translatableLootLine`) are the Adventure path, used where client-side translation of vanilla item names matters. Prefer these for item lore over raw strings.

## Updating shipped language files

`updates/LanguageUpdater` tops up user files with new keys, and a message the user already has keeps
exactly the components they gave it. `ConfigMigrations.ITEM_DEFAULTS` deliberately only fills in
`<section>.default` keys in `items.yml`, because mob-specific sections omit keys **on purpose** to
inherit from `default`. Do not extend it to fill every section; that would break the inheritance.
See `../updates/AGENTS.md`.
