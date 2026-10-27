# Plan: simplify the language and GUI files

Status: proposed. Nothing here is implemented yet.

The MiniMessage approach follows common practice for Paper plugins, adjusted for what does not fit
SmartSpawner (see "MiniMessage" below).

## Why

A fresh install writes about 70 YAML files into `plugins/SmartSpawner/`:

| Folder | Files | Notes |
|---|---|---|
| `language/` | 7 locales × 8 files = 56 | Every bundled locale is extracted, not just the active one |
| `gui_layouts/` | 3 layouts × 3 files = 9 | Every bundled layout is extracted |
| root | 6 | `config.yml`, `spawner_mobs.yml`, `spawner_items.yml`, `sell_integration.yml`, `activity_log.yml`, `auraskills.yml` |

The file count is a symptom. What actually confuses server owners:

1. **`en_US_DonutSMP` and `en_US_DonutSMP_v2` are not languages.** 7 of their 8 files are
   byte-identical to `en_US`; only `gui.yml` differs. They are GUI themes stored as locales. To get
   the DonutSMP look an owner has to set two keys that must agree, `language: en_US_DonutSMP` and
   `gui_layout: DonutSMP`. Setting one without the other gives a half-styled GUI.
2. **One button lives in two folders.** Slot, material and action are in
   `gui_layouts/<layout>/main_gui.yml`; its name and lore are in `language/<locale>/gui.yml`.
3. **Files are split along code lines, not along what the owner sees.** `messages.yml` vs
   `command_messages.yml` and `gui.yml` vs `command_gui.yml` exist because `LanguageManager` has two
   sections for each. `hologram.yml` holds a single key. `iridium_skyblock.yml` is written for every
   locale even on servers without IridiumSkyblock.
4. **Key names are inconsistent.** Titles are flat (`gui_title_main`, `gui_title_storage`) while
   their buttons are separate top-level keys with mixed suffixes (`spawner_storage_item`,
   `sell_button`, `button_sell_cancel`, `navigation_button_previous`). Messages are one flat list of
   26 keys with no grouping (`drop_failed`, `no_exp`, `spawner_different`).
5. **Comments are long and decorated.** Box banners (`# ─────`), a 15-line structure block at the top
   of `messages.yml`, and the same 7-line placeholder list repeated above every item in `gui.yml`
   (72 comment lines in `en_US/gui.yml` alone). There is no docs page for language files to point at
   instead.
6. **Only legacy colours.** `ColorUtil` handles `&a` and `&#RRGGBB`. No gradients, no MiniMessage,
   which most current plugins and owners expect.
7. **Custom locales are never migrated.** `LanguageUpdater` only walks `SUPPORTED_LANGUAGES`. An owner
   who made `language/de_DE/` gets no renames and no new keys, so any key rename breaks them silently.
8. **Locale lists drift.** The set of locales is repeated in `LanguageFiles.SUPPORTED_LANGUAGES`, the
   `config.yml` comment, `README.md` and both `docs/**/configuration.md` tables.

## Goals

- One setting per decision: `language` picks the words, `gui_layout` picks the look.
- The data folder contains what the server uses, not every bundled option.
- An owner editing a message can guess which file and key it is in, and the comment above it says
  everything needed to edit it in one line.
- MiniMessage and legacy `&` codes both work, in any file, forever.
- **No server loses a customisation on upgrade**, including custom locale folders. Migration is
  automatic, version-less (`YamlMigrator` + `ConfigMigrations`, see `updates/AGENTS.md`), backed up
  before it touches anything, and safe to run twice.

## Release shape

Phases are separate PRs, in this order. Phases 1, 2 and 4 change what is on disk, so ship them in
the **same release** so owners go through one migration, not three. Phase 3 can ship earlier on its
own. Phases 5 and 6 are optional follow-ups.

| Phase | What | Changes files on disk |
|---|---|---|
| 1 | DonutSMP text moves into the layout | yes |
| 2 | Extract only the active locale and layout | yes (fewer files) |
| 3 | MiniMessage input support | no |
| 4 | Language format v2: merged files, renamed keys, clean comments | yes |
| 5 | Component output path (optional) | no |
| 6 | Per-player language (optional) | no |

## Phase 1: move DonutSMP text into the layout

Give a layout an optional text override file, `gui_layouts/<layout>/texts.yml`, with the same key
paths as `language/<locale>/gui.yml` (the phase 4 names, if 4 lands in the same release). Lookup
order for a GUI text key:

1. `gui_layouts/<active layout>/texts.yml`
2. `language/<active locale>/gui.yml`
3. `en_US` bundled default (existing missing-key behaviour)

Work:

- `GuiLanguageSection` (the spawner GUI instance only): consult the layout override first. Clear it in
  `LanguageManager.clearCache()` and reload it when the layout changes, in both
  `ReloadSubCommand.reloadAll` and `FolderConfigSubCommand` (`/ss gui_layout`).
- Ship `gui_layouts/DonutSMP/texts.yml` and `gui_layouts/DonutSMP_v2/texts.yml` containing only the
  keys that differ from `en_US/gui.yml`.
- Delete `language/en_US_DonutSMP*` from resources and from `SUPPORTED_LANGUAGES`.
- Migration: if `config.yml` has `language: en_US_DonutSMP[_v2]`, rewrite it to `en_US` and, if
  `gui_layout` is still `default`, set it to the matching layout. If the owner edited
  `language/en_US_DonutSMP*/gui.yml` (it differs from the last bundled copy), copy the differing keys
  into that layout's `texts.yml`. Leave the old folder in the backup (see phase 4) and log one line.
- Addon layouts registered through `GuiLayoutRegistry` have no `texts.yml` and fall through to the
  locale, which is today's behaviour.
- A non-English locale with the DonutSMP layout uses the layout's `texts.yml`, which is English.
  Allow an optional `texts_<locale>.yml` next to it later; start with the single file.

## Phase 2: extract only what is active

- `LanguageFiles.saveDefaultFiles` and `LanguageUpdater`: extract and update the active locale, plus
  **every** locale folder that already exists on disk, bundled or custom (fixes problem 7).
- Same for `GuiLayoutUpdater` and `LAYOUT_NAMES`.
- `/ss language` and `/ss gui_layout` (`FolderConfigSubCommand.getAvailableFolderNames`) list bundled
  names plus folders on disk, and extract a bundled one the first time it is selected.
- `SUPPORTED_LANGUAGES` stays the single list the tab completer and extractor read.

Result: a fresh install has 1 locale and 1 layout instead of 7 + 3.

## Phase 3: MiniMessage input support

Every language string, and the layout `texts.yml`, accepts MiniMessage, legacy `&` codes, or both
mixed in one string. Nothing on disk changes in this phase.

### Parsing

A common approach decides per string: contains `<` means MiniMessage, otherwise legacy. **That rule
does not work here.** Shipped strings already contain literal angle brackets in usage lines, for example
`command_messages.yml` `set.usage`: `/ss set <stack_size|range|delay> <value>`, and those strings also
contain `&#3498db`, so they are legacy and mixed at the same time. Instead, normalise every string the
same way, once, at load:

1. Replace legacy tokens with MiniMessage tags by regex, keeping legacy semantics: a colour code
   becomes `<reset><color>` (legacy colours clear bold and italic), `&l` becomes `<bold>`, `&r`
   becomes `<reset>`, `&#RRGGBB` becomes `<reset><#RRGGBB>`. Section signs (`§`) are treated the same.
2. Replace `{name}` placeholders with `<name>` tags.
3. Parse with a MiniMessage instance that leaves unknown tags as literal text (the default,
   non-strict). `<stack_size|range|delay>` stays literal. Shipped strings escape literal brackets
   that could collide with a placeholder name (`\<value>`), and the docs say to do the same.

### Output, without touching 121 call sites

Today every GUI title, item name, lore line and message is a `§` string passed to Bukkit
(`setDisplayName`, `setLore`, `createInventory(…, String)`, `sendMessage(String)`). Phase 3 keeps that:
the parsed `Component` is serialised back to a `§` string with
`LegacyComponentSerializer.legacySection()` plus hex support. Gradients and hex survive as per-letter
colours. `click`, `hover` and `insertion` do not survive a legacy string; that is what phase 5 is for.
Document it as a known limit.

### Placeholders and small caps

- Placeholder values are inserted with `Placeholder.unparsed` so a player name, world name or item name
  can never inject formatting. Values that are already formatted lines (`{loot_items}`) use
  `Placeholder.component`.
- `{ᴇɴᴛɪᴛʏ}` is a placeholder whose name is unicode small caps. MiniMessage tag names must be
  `[a-z0-9_-]`, so add `{entity_small}` as the documented name and keep `{ᴇɴᴛɪᴛʏ}` as a permanent
  alias (it is inside owners' values, which migration never rewrites).
- Add a custom `<smallcaps>…</smallcaps>` tag (a `TagResolver` backed by `SmallCapsFormatter`;
  MiniMessage has no built-in small caps tag as of 4.26.1). Shipped English files can then
  say `<smallcaps>Open storage</smallcaps>` instead of `ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ`, which translators can read. It
  is a no-op for scripts without small caps (CJK, Cyrillic).

### Performance

`CONTRIBUTING.md` puts performance first, and the spawner info lore is refreshed every second for
open GUIs. Parse templates once at load. For per-call placeholders keep the existing
`LanguageCache` keyed on the resolved input, and check `getCacheStats()` before and after on the dev
server. `ColorUtil.translateHexColorCodes` becomes a thin wrapper over the new parser so callers outside
`language/` keep working.

## Phase 4: language format v2

One migration that merges files, renames keys and replaces comments, so owners only see the
structure change once.

### Files per locale

| New file | Contains |
|---|---|
| `messages.yml` | `messages.yml` + `command_messages.yml` (command ones under `command:`) |
| `gui.yml` | `gui.yml` + `command_gui.yml` (spawner GUIs by screen, command GUIs under `command:`) |
| `items.yml` | `items.yml` + `hologram.yml` (under `hologram:`) |
| `formatting.yml` | unchanged |

`iridium_skyblock.yml` moves to `language/<locale>/hooks/iridium_skyblock.yml` and is only written
when the IridiumSkyblock hook is active.

### Key naming rules

- `snake_case`, matching `config.yml`. Do not mix in kebab-case.
- Group by what the owner sees: a screen, then the thing on it. A button is
  `<screen>.<button>` holding `name` and `lore`.
- A screen's title is `<screen>.title`, with variants as `title_<variant>`.
- Messages are `<topic>.<event>`. No `_item`, `_button` or `gui_title_` prefixes and suffixes.

### Rename table

Messages (`messages.yml`):

| Old | New |
|---|---|
| `missing_message_key` | `missing_key` |
| `inventory_full`, `click_too_fast` | `general.inventory_full`, `general.click_too_fast` |
| `action_in_progress`, `action_failed`, `action_not_ready` | `general.action_in_progress`, `general.action_failed`, `general.action_not_ready` |
| `spawner_activated`, `entity_changed` | `spawner.activated`, `spawner.entity_changed` |
| `spawner_storage_empty`, `storage_in_use` | `storage.empty`, `storage.in_use` |
| `no_exp`, `exp_collected`, `exp_collected_with_mending` | `exp.none`, `exp.collected`, `exp.collected_mending` |
| `spawner_different`, `spawner_stack_full`, `spawner_stack_success` | `stack.different_type`, `stack.full`, `stack.success` |
| `break_no_permission`, `break_silk_touch`, `break_tool_required` | `break.no_permission`, `break.silk_touch_required`, `break.wrong_tool` |
| `drop_failed`, `sneak_break_blocked` | `break.no_drop`, `break.sneak_blocked` |
| `no_sellable_items`, `sell_success` | `sell.nothing_to_sell`, `sell.success` |
| `sell_and_exp_success`, `sell_and_exp_success_with_mending` | `sell.success_with_exp`, `sell.success_with_exp_mending` |
| `command_messages.yml`: `player_only`, `no_permission` | `command.player_only`, `command.no_permission` |
| `command_messages.yml`: `<cmd>.<key>` | `command.<cmd>.<key>` (inner keys unchanged) |

Spawner GUIs (`gui.yml`):

| Old | New |
|---|---|
| `gui_title_main.single_spawner`, `.stacked_spawner` | `main.title`, `main.title_stacked` |
| `spawner_storage_item`, `spawner_info_item`, `exp_info_item` | `main.storage`, `main.info`, `main.exp` |
| `gui_title_storage` | `storage.title` |
| `gui_title_storage_single_spawner` (read by `SpawnerStorageUI` but missing from every shipped locale today; check how it falls back, then ship it) | `storage.title_single` |
| `navigation_button_previous`, `navigation_button_next` | `storage.previous_page`, `storage.next_page` |
| `sell_button`, `sell_and_exp_button`, `return_button` | `storage.sell`, `storage.sell_and_exp`, `storage.back` |
| `take_all_button`, `item_filter_button`, `sort_items_button` | `storage.take_all`, `storage.filter`, `storage.sort` |
| `drop_page_button`, `collect_exp_button`, `storage_spawner_info_button` | `storage.drop_page`, `storage.collect_exp`, `storage.info` |
| `gui_title_sell_confirm`, `button_sell_cancel`, `button_sell_confirm` | `sell_confirm.title`, `sell_confirm.cancel`, `sell_confirm.confirm` |
| `button_sell_confirm_with_exp`, `button_sell_info` | `sell_confirm.confirm_with_exp`, `sell_confirm.summary` |
| `gui_title_filter_config`, `filter_divider` | `filter.title`, `filter.divider` |
| `filter_item_allowed`, `filter_item_blocked` | `filter.allowed`, `filter.blocked` |

Command GUIs (`command_gui.yml` into `gui.yml` under `command:`) follow the same rules:
`gui_title_server_selection` becomes `command.list.servers.title`, `world_buttons` becomes
`command.list.worlds.buttons`, `gui_title_prices` becomes `command.prices.title`, and so on. Write the
full table in `ConfigMigrations` while implementing; it is mechanical, and the inner keys of each
section mostly stay.

`items.yml`: `smart_spawner`, `item_spawner`, `vanilla_spawner` keep their names (they are already
grouped well). `hologram.yml` `hologram_text` becomes `items.yml` `hologram.lines`.

Before finalising a name, `grep` the Java code for the old key string: several are built
dynamically (`SpawnerStorageUI` picks `navigation_button_previous` or `_next` from a variable).

### Migration

`YamlMigrator.Rename` works inside one file. Moving keys across files needs a one-time step,
`LanguageRestructure`, in `updates/`, that runs before `LanguageUpdater`:

1. **Detect** per locale folder: the old layout is present if any of `command_messages.yml`,
   `command_gui.yml`, `hologram.yml` exists, or `messages.yml` has an old top-level key. No version
   number.
2. **Back up** once per startup: copy the whole `language/` folder (and `gui_layouts/` for phase 1)
   to `language.backup-<yyyy-MM-dd>/` before writing anything. Never overwrite an existing backup.
3. **Run for every folder under `language/`**, bundled or custom. A custom folder (no bundled
   resource) gets renames and the merge but no top-up from defaults; missing keys fall back to
   `en_US` at runtime.
4. **Merge into the new files using the shipped file as the template.** Start from the bundled new
   `messages.yml` text (so the new short comments are there), then set every value the owner had,
   under its new path. For a custom locale with no bundled file, start from `en_US`'s template and
   overlay their values.
5. **Never drop a value.** A key the rename table does not know (owner-added, or from a newer fork) is
   kept under the same path inside the target file, and listed once in the log.
6. **New wins over old.** If an owner already has a value at the new path, keep it and discard the
   old one, as `YamlMigrator` already does.
7. **Then delete the old files** (they are in the backup) and log one line per locale:
   `Migrated language/de_DE to the new format. Backup: language.backup-2026-11-02/`.
8. **Idempotent.** A second run finds nothing to detect and does nothing.

Also:

- Owners' **values are never rewritten**: legacy colours, `{ᴇɴᴛɪᴛʏ}` and old placeholders keep working
  because phase 3 accepts them. Only shipped default text is converted to MiniMessage.
- Add the per-file in-place renames (anything later) to `ConfigMigrations` as usual, and apply the
  same table to `gui_layouts/*/texts.yml`.
- Keep an **old-to-new key alias map** in `LanguageManager` for one release, so an addon or fork that
  still asks for `sell_button.name` resolves, with one warning per key. Remove it the release after.
- `LanguageUpdater.isMessageEntry` decides ownership by nesting depth. Messages now sit one or two
  levels deeper (`break.wrong_tool`, `command.list.no_spawners_found`); recheck it.
- `ConfigMigrations.ITEM_DEFAULTS` keeps its "only `<section>.default`" rule for `items.yml`.

### Comments

Rewrite every shipped language file to the rules in the `write-language-files` skill
(`.claude/skills/write-language-files/SKILL.md`), which is the source of truth for key names, comment
style, colours and translation checks. Summary:

- **Header: three lines at most.** What the file is, the docs link, the colour formats.
- **No banners, no structure blocks, no repeated placeholder lists.** A blank line between groups,
  and a one-line comment above a group only when the key names do not say it.
- **Placeholders: one line above the key that uses them, listing only those.**
- **Everything longer moves to a new docs page**, `docs/docs/language.md` (and `docs/vi/...`): message
  components (`message`, `title`, `subtitle`, `action_bar`, `sound`, `enabled`), the full placeholder
  table per screen, MiniMessage and legacy examples, small caps, and how to add a custom locale. Use the
  `write-docs` skill and add it to the VitePress sidebar.

Before (`messages.yml` today, 17 comment lines before the first key):

```yaml
# SmartSpawner – messages.yml
# Each entry supports: message, title, subtitle, action_bar, sound, enabled.
#
#   MESSAGE STRUCTURE:
#   message_name:               # Unique identifier for the message
#     enabled: true/false       # Enable or disable this entire message (default: true if not specified)
#     ...
# ──────────────────────────────────────────────────────
#              Player Interaction (General)
# ──────────────────────────────────────────────────────
spawner_activated:
```

After:

```yaml
# SmartSpawner messages
# Full guide: https://docs.smartspawner.site/docs/language
# Colours: MiniMessage (<red>, <#5DB5F1>, <gradient:#5DB5F1:#FFFFFF>) or legacy (&c, &#5DB5F1).
# Each message can use message, title, subtitle, action_bar and sound. enabled: false mutes it.

prefix: '<#3498db><smallcaps>SmartSpawner</smallcaps> <dark_gray>⏩ <reset>'

general:
  inventory_full:
    message: '<#FF4444>Your inventory is full.'

sell:
  # {amount} {price}
  success:
    message: 'You sold <yellow>{amount}</yellow> item(s) for <green>${price}</green>.'
```

And in `gui.yml`, one placeholder line per button instead of seven per field:

```yaml
main:
  # {current_items} {max_slots} {percent_storage_rounded} {total_sell_price} {loot_items} {entity}
  storage:
    name: '<white><smallcaps>Open</smallcaps> <#5DB5F1><smallcaps>Storage</smallcaps>'
    lore: [...]
```

Target: under 15 comment lines in any language file.

## Phase 5 (optional): Component output

Move the senders to Adventure: `sendMessage(Component)`, `ItemMeta.displayName(Component)` and
`lore(List<Component>)` with italic turned off, `createInventory(holder, size, Component)`. This
unlocks `<click>` and `<hover>` in chat (clickable `/ss list` results, docs links), removes the legacy
round-trip, and lets vanilla item names use `translatable` so they show in each client's language.
`LanguageComponentFormatter` already does part of this for loot lore; extend it rather than adding a
second path. Large but mechanical; split it by package (messages, then GUIs, then items).

## Phase 6 (optional): per-player language

Resolve every string against each viewer's client locale through a `MiniMessageTranslator` on
Adventure's `GlobalTranslator`, with fallback exact, same language, configured `language`, `en_US`,
behind a `language-auto-detect` toggle. Worth doing once phase 5 is done, with two caveats: Paper only
auto-renders chat and inventory titles, so item names and lore must be rendered eagerly with `player.locale()`; and the translator must
be removed first in `onDisable`. Holograms are shared between players, so they stay on the server
language.

## Not changing

- Root files (`config.yml`, `spawner_mobs.yml`, `sell_integration.yml`, ...) each own one area and are
  documented on the site.
- The `{placeholder}` syntax in files. It is what owners know; MiniMessage `<placeholder>` is accepted
  as well but not required.

## Verification

Each phase gets a manual pass on the dev server (`playtest-server` skill) plus unit tests for the
pure parts, next to `SellIntegrationConfigUpdaterTest`:

- **Parser tests (phase 3):** legacy only, MiniMessage only, mixed, the `set.usage` string with literal
  brackets, `&l` followed by a colour (bold must clear), `{ᴇɴᴛɪᴛʏ}` and `{entity_small}`, an injected
  `<red>` inside a placeholder value stays literal.
- **Migration tests (phase 1 and 4)** on fixture folders: a stock old install; an install with edited
  values in every old file; a custom `de_DE` with only some keys and one unknown key; a half-migrated
  folder (new and old files both present); running the migration twice. Assert no value is lost, the
  backup exists, unknown keys survive, the second run changes nothing.
- **In game:** fresh data folder shows only the expected files; an upgraded folder with
  `language: en_US_DonutSMP` keeps its look; every GUI renders without missing-key warnings;
  `/ss reload`, `/ss language`, `/ss gui_layout` switch without a restart; a gradient in a GUI title
  and a hex colour in an action bar both render.
- Compare `getCacheStats()` and a spark profile of an open storage GUI before and after phase 3.

## Docs and changelog

- New `docs/docs/language.md` and `docs/vi/docs/language.md` (phase 3 for MiniMessage, extended in
  phase 4), linked from every language file header.
- `docs/docs/configuration.md` and the Vietnamese copy: drop the DonutSMP rows, explain `texts.yml`
  under `gui_layout`.
- `CHANGELOG.md` under `## Unreleased`, written with the `write-docs` skill, with a Notes line saying
  the language files are migrated automatically, where the backup is, and that DonutSMP is now chosen
  with `gui_layout` only.
- Update `language/AGENTS.md`, `updates/AGENTS.md` and `spawner/gui/AGENTS.md` for the new files,
  key names, parser and lookup order.
