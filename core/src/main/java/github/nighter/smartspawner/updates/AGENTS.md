# updates/

Keeping a user's on-disk YAML in step with the shipped defaults, plus the update checker. This is the
package that decides whether a server that upgrades keeps its settings or silently loses them.

| File | Role |
|---|---|
| `YamlMigrator` | The generic engine. Version-less, runs every startup, for every file |
| `ConfigMigrations` | The registry of what to migrate: rename tables and custom migrations |
| `ConfigUpdater` | Applies the engine to `config.yml` |
| `LanguageUpdater` | Applies it to the language files |
| `GuiLayoutUpdater` | Applies it to `gui_layouts/**/*.yml` |
| `UpdateChecker`, `Version` | Notifies about new plugin releases |

## Version-less migration

Since 1.7.1 there is **no `config_version`**. `YamlMigrator` actively strips the legacy keys
`config_version`, `language_version` and `gui_layout_version` if it finds them. Do not reintroduce a
version number; that is the design decision this package exists to express.

The engine keeps the user's file as-is and only ever adds. Per file, in order:

1. Strip the legacy version keys.
2. Apply renames: move the user's value from `oldPath` to `newPath`, then delete `oldPath`. If the user already has a value at `newPath`, theirs wins and the old key is just removed.
3. Run the optional `CustomMigration` for anything a rename cannot express.
4. Add missing keys from the bundled defaults, carrying comments and inline comments across.
5. Save, **only if** something changed.

A file that does not exist yet is extracted verbatim from the bundled resource so its formatting and
comments survive intact.

Values of existing, unchanged keys are never touched. That is the contract; keep it.

## Sections the user owns

Step 4 is right for settings, where a missing key means "this option is new". It is wrong for a
curated list. `YamlMigrator.OwnedSection` marks sections whose *contents* belong to the user: once
they have such a section, nothing inside it is topped up. It is a record with two factories, which
differ only in what an **absent** section means:

| Factory | Absent section | Used by |
|---|---|---|
| `curated` | Stays absent, as long as the user still has its parent | `SpawnerSettingsConfig`, for `*.loot` |
| `restoredWhenAbsent` | Refilled from the defaults | `LanguageUpdater`, for one message entry |

`curated` is right for a drop list: deleting a mob's whole `loot` block has to stick.
`restoredWhenAbsent` is right for anything the code looks up **by name**, because a genuinely missing
message key logs a warning and shows the player a missing-key notice, so refilling it is kinder than
honouring the deletion.

Without any of this, two things break in `spawner_mobs.yml`. A drop the server owner deleted
comes back on the next start, and if the shipped file ever relabels an entry the user ends up holding
both the old and the new one, so that item drops twice. In `messages.yml` the equivalent is deleting
`message` to leave only `action_bar`: the chat line came back and the player got it twice.

Two traps if you touch `lockedSections`:

- The owned sections are **snapshotted before** the top-up runs. Deciding per key while writing lets the first leaf of a brand new section create that section, after which the rest of it looks user-owned and is skipped, leaving it half filled.
- A message entry is identified by what it **directly contains** (`message`, `title`, `subtitle`, `action_bar`, `sound`, `enabled`), never by depth. `messages.yml` is flat but `command_messages.yml` nests messages under their command, and locking a command section whole would stop any message a later version adds to it from ever reaching the file.

## Renaming or removing a config key

**Add a `Rename` entry to the matching list in `ConfigMigrations`.** Without it, upgrading servers get
the shipped default silently added under the new name while their configured value disappears with the
old key. There is no test for this, so it is easy to miss in review.

```java
new YamlMigrator.Rename("old.path.here", "new.path.here")
```

Renames are applied in list order, so a key renamed twice across versions needs both hops present, old
to intermediate to current, in that order.

Language files have their own lists, reached through `ConfigMigrations.forLanguageFile(type)`; they
start empty. Renaming a message key without an entry there is worse than for a config key: the plugin
looks messages up by name, so the owner's wording is stranded under the old name while the new name
arrives carrying the shipped default, and their customisation silently stops being used. Renaming the
message entry itself is enough, its components move with it. The switch in `forLanguageFile` is
exhaustive, so a new `LanguageFileType` will not compile until its list is declared.

For a value change rather than a key change (an enum constant renamed, a mode merged), use a
`CustomMigration`. `CONFIG_VALUES` is the example: `COINSENGINE` becomes `EXCELLENTECONOMY` and
`DATABASE` becomes `MYSQL`.

Deleting a key needs no entry; it simply stops being topped up. But nothing removes it from existing
files, so old keys linger harmlessly.

## The three custom migrations, and why they are custom

`CONFIG_VALUES` rewrites values based on a condition, which a static rename cannot express.

`GUI_LAYOUT` handles legacy click and sound paths in the layout files with regex, because the shape
changed rather than the name: a leaf `slot_x.click` became the section `slot_x.click.action`. It
removes **all** old paths before writing any new one, since setting `slot_x.click.action` on a config
that still holds a leaf at `slot_x.click` would clobber the fresh section. If you touch this, preserve
that two-pass order.

`ITEM_DEFAULTS` tops up **only** `<section>.default` keys in `items.yml`, for `smart_spawner`,
`item_spawner` and `vanilla_spawner`. Entity-specific sections deliberately omit keys so they inherit
from `default`. Blindly adding every default key to every mob section, or adding whole new mob
sections, would break that inheritance. This is why `items.yml` is migrated with `addMissing = false`
and lets the custom migration decide exactly what to add.

## Adding a new config file

Give it an updater call that mirrors `ConfigUpdater`: locate the file in the data folder, get the
bundled resource as an `InputStream`, and call `YamlMigrator.migrate(file, stream, renames, custom,
addMissing, logger)`. Then add its rename list to `ConfigMigrations` even if it starts empty, so the
next rename has an obvious home.

Every one of these must run **before** the component that reads the file is constructed.
`configUpdater.checkAndUpdateConfig()` is called at the top of `initializeServices()` for exactly this
reason.

## Moving keys between files, and the one file that is rewritten

`YamlMigrator` only ever sees one file, so a key moving from one file to another cannot be a
`Rename`. There are two of these, both from 1.8.0, and both live outside this package next to the
component that reads the file.

`SellIntegrationConfigUpdater` (in `hooks/economy/`) is the simpler one: the `sell_integration` section
of `config.yml` and the whole of `item_prices.yml` became `sell_integration.yml`. It copies each source
in, deletes it, and only then calls the migrator, for the same reason as below. `item_prices.yml` is
deleted rather than left behind, so a price the owner removes later cannot be re-imported on the next
start.

`ActivityLogConfigUpdater` is the harder one: in 1.8.0 `discord_logging.yml` became
`activity_log.yml` and the `logging` section of `config.yml` became its `file` section. It renames the
file on disk, copies the old section across, deletes it from `config.yml`, and only then calls the
migrator, because a top-up that ran first would fill those keys with the shipped defaults and the
user's values would have nowhere to land.

That same class is also the one place that breaks the "only ever add" contract. Because *every* key of
that file moved, a migrated file would keep comments describing keys that are no longer there, under a
header naming a file that no longer exists. So when it sees the old layout it writes the user's values
into a fresh copy of the bundled file instead, once. The trigger is a shape check
(`isLegacyLayout`, no `discord` section), never a version number, and it must stay one that cannot
match a file that has already been converted, or the file would be rewritten on every startup.
