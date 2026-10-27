# Plan: simplify the language and GUI file structure

Status: proposed. Nothing here is implemented yet.

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
4. **Locale lists drift.** The set of locales is repeated in `LanguageFiles.SUPPORTED_LANGUAGES`, the
   `config.yml` comment, `README.md` and both `docs/**/configuration.md` tables. `es_ES` was missing
   from the config comment and docs until zh_CN was added.

## Goals

- One setting per decision: `language` picks the words, `gui_layout` picks the look.
- The data folder contains what the server uses, not every bundled option.
- An owner editing a message can guess which file it is in.
- No server loses customisations on upgrade. Migration is automatic and version-less
  (`YamlMigrator` + `ConfigMigrations`, see `updates/AGENTS.md`).

## Phases

Each phase ships on its own and is safe to release alone. Do them in order; phase 3 is the most
invasive and is optional.

### Phase 1: move DonutSMP text into the layout

Give a layout an optional text override file, `gui_layouts/<layout>/texts.yml`, with the same key
paths as `language/<locale>/gui.yml`. Lookup order for a GUI text key:

1. `gui_layouts/<active layout>/texts.yml`
2. `language/<active locale>/gui.yml`
3. `en_US` bundled default (existing missing-key behaviour)

Work:

- `GuiLanguageSection` (the `gui()` instance only): consult the layout override first. The override
  must be cleared in `LanguageManager.clearCache()` and reloaded when the layout changes, which
  happens in `ReloadSubCommand.reloadAll` and in `FolderConfigSubCommand` (`/ss gui_layout`). Both
  paths need it.
- Ship `gui_layouts/DonutSMP/texts.yml` and `gui_layouts/DonutSMP_v2/texts.yml` containing only the
  keys that differ from `en_US/gui.yml` (the 168 / 175 differing lines today).
- Delete `language/en_US_DonutSMP*` from resources and from `SUPPORTED_LANGUAGES`.
- Migration in `updates/`: if `config.yml` has `language: en_US_DonutSMP[_v2]`, rewrite it to
  `en_US` and, if `gui_layout` is still `default`, set it to the matching layout. If the user's
  `language/en_US_DonutSMP*/gui.yml` was edited (differs from the old bundled copy), copy their
  differing keys into the layout's `texts.yml` so the customisation survives. Leave the old folder in
  place and log one line telling the owner it is no longer used.
- Addon layouts registered through `GuiLayoutRegistry` have no `texts.yml`; they fall through to the
  locale, which is today's behaviour.
- Decide whether a non-English locale with the DonutSMP layout should use the layout's English
  `texts.yml` or the locale's `gui.yml`. Proposal: `texts.yml` may be locale-keyed
  (`texts.yml` default, `texts_<locale>.yml` optional) so a translator can theme it later. Start
  with the single file.

Result: owners pick DonutSMP with one key, and any language works with any layout.

### Phase 2: extract only what is active

- `LanguageFiles.saveDefaultFiles` and `LanguageUpdater.checkAndUpdateLanguageFiles`: extract and
  update the active locale only, plus any locale folder that already exists on disk (so existing
  installs keep getting new keys).
- Same for `GuiLayoutUpdater` and `LAYOUT_NAMES`.
- `/ss language` and `/ss gui_layout` currently list folders on disk
  (`FolderConfigSubCommand.getAvailableFolderNames`). Change them to list bundled names plus folders
  on disk, and extract a bundled one the first time it is selected.
- Make `SUPPORTED_LANGUAGES` the single list that the tab completer and the extractor read. Keep the
  README and docs tables in sync by hand, but point at this list in `language/AGENTS.md`.

Result: a fresh install has 1 locale (8 files) and 1 layout (3 files) instead of 56 + 9.

### Phase 3 (optional): fewer files per locale

Merge by what the owner sees:

| New file | Contains |
|---|---|
| `messages.yml` | `messages.yml` + `command_messages.yml` (command ones under a `commands:` section) |
| `gui.yml` | `gui.yml` + `command_gui.yml` (under `spawner:` and `commands:`) |
| `items.yml` | `items.yml` + `hologram.yml` |
| `formatting.yml` | unchanged |

`iridium_skyblock.yml` moves out of the locale folder and is only written when the IridiumSkyblock
hook is active.

The top-level keys of the merged pairs do not collide today (checked for `en_US`), but nesting under
`commands:` changes every key path the code reads, so this phase touches many call sites:

- `LanguageFileType`, `LocaleData`, `LanguageFiles.loadLocale`, `LanguageUpdater`.
- Every `commandGui()` and command message lookup, or a prefix applied inside the section so call
  sites stay the same. Prefer the prefix: smaller diff, same keys in code.
- Migration: if the old split files exist, merge them into the new file under the new paths, rename
  the old ones to `*.yml.old`, and log once. `ConfigMigrations.ITEM_DEFAULTS` must keep its
  "only `<section>.default`" rule for the item part.
- `MessageLanguageSection` ownership rules in `LanguageUpdater.isMessageEntry` depend on nesting
  depth; recheck them for the `commands:` level.

Trade-off: `gui.yml` grows to about 600 lines. Worth it only if phase 1 and 2 land and owners still
report trouble finding strings. Revisit after phase 2.

### Not changing

- Root files (`config.yml`, `spawner_mobs.yml`, `sell_integration.yml`, ...) each own one area and
  are documented on the site. Leave them.
- Comment style in shipped YAML follows the `write-config-comments` skill. As files are touched,
  move repeated placeholder lists to one block per file or to the docs site instead of repeating
  them above every item.

## Verification

There are few automated tests, so each phase needs a manual pass on the dev server
(`playtest-server` skill):

- Fresh data folder: only the expected files appear, GUIs render, `/ss language` and
  `/ss gui_layout` tab-complete bundled options.
- Upgrade: copy a data folder from the previous release with `language: en_US_DonutSMP`, a
  customised DonutSMP `gui.yml`, and an edited `command_messages.yml`. Start, confirm the
  customisations are still visible and no missing-key warnings are logged.
- `/ss reload` and `/ss gui_layout DonutSMP` → `default` → `DonutSMP` switch text without a restart.
- Add a unit test per migration step next to `SellIntegrationConfigUpdaterTest`.

## Docs and changelog

- `docs/docs/configuration.md` and `docs/vi/docs/configuration.md`: drop the DonutSMP rows from the
  language table, explain `texts.yml` under `gui_layout`.
- `CHANGELOG.md` under `## Unreleased`, written with the `write-docs` skill, including a Notes line
  that the DonutSMP language folders are no longer used.
- Update `language/AGENTS.md` and `spawner/gui/AGENTS.md` for the new lookup order.
