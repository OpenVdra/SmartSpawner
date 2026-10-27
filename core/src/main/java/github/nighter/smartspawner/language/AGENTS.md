# language/

Every player-visible string. No literal text goes to a player anywhere else in the plugin.

## Two entry points

| Use | Class |
|---|---|
| Sending something to a player or console | `MessageService` |
| Getting text to build an ItemStack or GUI | `LanguageManager` |

`MessageService.sendMessage(sender, key[, placeholders])` is the one to reach for in listeners and
commands. One call does chat + title + subtitle + action bar + sound, in the sender's language,
because a single message key can declare all of them. Do not send a title or play a sound separately
alongside `sendMessage` or the player gets it twice.

`sendConsoleMessage` logs the chat line as plain text, in the server language.

A missing key is not silent: it logs a warning and tells the player via the `missing_key` message.
Key existence is cached in `keyExistsCache`, and `clearKeyExistsCache()` is called from
`ReloadSubCommand` and `FolderConfigSubCommand`, **not** from `SmartSpawner.reload()`. Any new reload
path has to clear it explicitly or newly added keys stay invisible until restart.

## Output is Components

Everything comes back as Adventure `Component`s and goes out through the Component APIs:
`sendMessage(Component)`, `showTitle`, `sendActionBar`, `ItemMeta.displayName`/`lore`,
`createInventory(holder, size, Component)`, `TextDisplay.text`. Names, lore and other item text come
back with italic turned off (`TextFormatter.noItalic`); titles do not. The only string outputs left
are `InventoryView.setTitle` (storage retitle, via `TextFormatter.toLegacy`) and the IridiumSkyblock
`Item`, whose API takes strings.

## What text may do: `format/TextFormatter`

The one parser. Every string accepts MiniMessage, legacy `&`/section-sign codes and `&#RRGGBB`,
mixed. Legacy codes are rewritten to tags first (a legacy colour after a legacy decoration emits
`<reset>`, keeping legacy semantics). Unknown tags stay literal, so usage lines like
`<stack_size|range|delay>` survive; shipped text escapes placeholder-like brackets as `\<value>`.

Two policies, and the choice is a security decision:

| Method | Used for | Extra tags |
|---|---|---|
| `parse` | items, GUI titles, titles, action bars, boss bars, holograms, dialogs | none |
| `parseChat` | the `message` part of a message only (`MessageLanguageSection`) | `<click>`, `<hover>`, `<insert>` |

Neither resolves `<nbt>`, `<selector>` or `<score>`. A blocked tag stays visible text.

- Never use `parseChat` for anything that ends up on an item. A click event stored on an item name travels with the item, and a chat-showcase plugin can make it live for whoever clicks.
- Placeholder values are **never parsed**: plain values go in through `Placeholder.unparsed`, values carrying legacy codes through `legacyValue` (a tokenizer that turns codes into styles and inserts the text between them as is), component values through `inert`, which strips click, hover and insertion. Placeholders are bound as generated tags, not pasted into the template, so a value can never land inside an owner's `<click:run_command:...>` argument. `TextFormatterSafetyTest` covers each of these; keep it passing.
- Do not build text by `String.replace`-ing a value into a template and then parsing it. That parses the value. Pass it as a placeholder, or as a component placeholder. A click that code adds itself (the `/ss near` "view in GUI" hint) uses `ClickEvent.callback`, never `run_command` built from text.
- `{ᴇɴᴛɪᴛʏ}` and `{entity_small}` alias each other. `<smallcaps>` is a custom tag (`SmallCapsFormatter`).

## Files, keys and lookup

`file/LanguageFileType` lists the four files per locale: `messages.yml`, `gui.yml`, `items.yml`,
`formatting.yml`. Command messages are under `command.` in `messages.yml`; command GUIs under
`command.` in `gui.yml`, reached through `commandGui()`, whose keys are written **without** that
prefix. `hooks/iridium_skyblock.yml` is read by the hook itself. Keys are grouped by topic (messages)
or screen (GUI); `file/LegacyLanguageKeys` holds the 1.7 to 1.8 table.

`file/LanguageRepository` holds a `LocaleData` chain: the server locale (`language`) falls back to the
bundled `en_US`, and a player locale falls back to the server's. Lookup per section:

| Section | Reached via | Fallback |
|---|---|---|
| `MessageLanguageSection` | `MessageService`, `texts(sender).getMessages()` | per **entry**: the first locale that has the message owns all its components, so a removed component stays removed |
| `GuiLanguageSection` (two instances) | `gui(viewer)`, `commandGui(viewer)` | layout `texts_<locale>.yml`, layout `texts.yml`, then per key down the chain; a lore whose button the owner has but whose `lore` they removed stays empty |
| `ItemLanguageSection` | `getItemName`, `getItemLore`, loot lines | none: server locale only, mob sections inherit from `default` on purpose |
| `FormattingLanguageSection` | `formatNumber`, `getFormattedMobName` | none: server locale only |
| `HologramLanguageSection` | `getHologramText` | `items.yml` `hologram.lines`, then the old `hologram.text` in config.yml |

The two GUI sections are distinct. Spawner GUIs use `gui()`; the command-driven GUIs (`/ss list`,
`/ss near`, `/ss prices`, `/ss editloot`) use `commandGui()`. Picking the wrong one gives a missing-key
text even though the text exists.

An old key asked for by an addon (`sell_button.name`, `no_exp`) still resolves through
`LegacyLanguageKeys`, with one warning per key. This alias is for one release; remove it after.

## Per-player language (`language_auto_detect`)

`LanguageManager.texts(sender)`, `gui(viewer)` and `commandGui(viewer)` return the viewer's locale
when auto-detect is on and a folder for their client locale (exact, then same language) exists;
otherwise the server texts. Each locale has its own `LocaleTexts` with its own caches. Use the viewer
overloads wherever a GUI is built for one player, and put `localeKey(viewer)` into any cache of built
`ItemStack`s, as `SpawnerMenuUI` and `SpawnerStorageUI` do, or players see each other's language.
Spawner items, holograms, mob names, number formats and the console stay on the server locale.

Only the active locale and locale folders already on disk are extracted and updated
(`LanguageFiles.localesInUse`), plus every bundled one while auto-detect is on.
`SUPPORTED_LANGUAGES` is the list of bundled ones. Use the `write-language-files` skill when adding,
renaming, translating or commenting keys.

**A new key must be added to every locale directory.** There is no compile-time check; a key only in
`en_US` shows English to everyone else. `en_US` is the reference.

## Caching

`cache/LanguageCache` holds one locale's parsed `Component`s (display and chat separately), keyed by
the text and its placeholders, plus mob, material and small caps name caches. GUI sections have their
own name and lore caches. `reloadLanguages()` clears every locale's caches; a new cache must be added
to `LocaleTexts.clearCache()`.

A placeholder map with a value that changes constantly (a countdown, a coordinate) pollutes the
cache. `{time}` is left unresolved in the cached item and replaced on the Component
(`SpawnerMenuUI.applyTimerPlaceholder`, `TimerUpdateService`); holograms parse their template once with
slots and fill them with `replaceText`. Do the same for any other per-tick value.

`format/NumberFormatter` produces the compact number strings shown in GUIs. Never `String.valueOf` a
number into player-facing text; locales differ.

## Updating shipped language files

`updates/LanguageRestructure` moves 1.7 folders to the 1.8 layout once, then `updates/LanguageUpdater`
tops up user files with new keys, and a message the user already has keeps exactly the components
they gave it. Both run before `LanguageManager` loads anything. `ConfigMigrations.ITEM_DEFAULTS`
deliberately only fills in `<section>.default` keys and `hologram.lines` in `items.yml`, because
mob-specific sections omit keys **on purpose** to inherit from `default`. See `../updates/AGENTS.md`.
