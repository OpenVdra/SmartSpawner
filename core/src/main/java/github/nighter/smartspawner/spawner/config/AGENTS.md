# spawner/config/

Parsing the two spawner-content files into the runtime loot tables and the cage previews. This is where
a `spawner_mobs.yml` / `spawner_items.yml` entry becomes an `EntityLootConfig` full of prebuilt
`LootItem` templates. Consumed by `spawner/lootgen/`.

| File | Role |
|---|---|
| `SpawnerSettingsConfig` | Loads `spawner_mobs.yml` into `MobDefinition`s. Legacy name `spawners_settings.yml` |
| `ItemSpawnerSettingsConfig` | Loads `spawner_items.yml` into `ItemDefinition`s. Legacy name `item_spawners_settings.yml` |
| `SpawnerFileLayout` | The shared file shape, and the one-time conversion of a 1.8 file to it |
| `SpawnerNames` | Resolves a typed name across both files for commands; reports names that clash |
| `SpawnerNameNotices` | Warns once per load about placed spawners whose entry is gone |
| `SpawnerHead` | A spawner's menu icon (`mob_head`), per entry |
| `LootEntryParser` | One config entry → a `LootItem` (amount range, chance, durability range, price) |
| `ConfiguredItemParser` | Builds the entry's template `ItemStack` from `item:` (material, `/give` component syntax, or `nbt:` + Base64) |
| `SpawnerMobHeadTexture` | The player-head textures used for mob icons and item-spawner heads in GUIs |
| `SpawnerDisplayConfigurator` | The vanilla cage preview (`EntitySnapshot` for mobs, captured item for item spawners) |
| `SpawnerConfigName` | `normalize()` canonicalizes a user-facing name once so lookups are O(1) |
| `SupersededConfigNotice` | Console notice, once, that a settings file was renamed in 1.8.0 |

## File layout and spawner identity

Both files share one shape (`SpawnerFileLayout`). A top-level key is a **base spawner** and is the
mob or item itself (`zombie:`, `diamond:`), with no type key. `custom_spawners:` holds any number of
**custom spawners**, each naming its type with `entity:` / `item:`. A custom spawner inherits head,
`drop_chance`, `nbt_data` and `experience` from the base spawner of its type when it leaves them out;
its loot is never inherited.

The entry name is the spawner's identity. It is stored in `SpawnerData.configName`, the database
`config_name` column and the item PDC (`spawner_config_name`). Every lookup of a stored name goes
through `resolve()`: exact name, then `aliases`, then a 1.8 `<type>_spawner` name read as the base
spawner. `SpawnerData.refreshDefinition()` rewrites a name found that way and marks the spawner dirty,
so placed spawners migrate on load. A name with no entry at all is **kept** (the spawner falls back to
its base spawner via `resolveOrBase`), so a typo in the file does not permanently rewrite spawners.
Compare item and spawner names through `resolve()`, never with `equals` on raw strings, or items saved
under an old name stop stacking.

A 1.8 file (top-level entries with a type key, no `custom_spawners`) is converted before anything else
reads it: `<type>_spawner` entries become base spawners, the rest move to `custom_spawners`, and the
original is copied to `<file>.1.8-backup`. For `spawner_mobs.yml` this runs as the `CustomMigration` of
`YamlMigrator`, so it happens before the top-up.

## Templates are resolved here, once

`ConfiguredItemParser` turns the `item:` field into a finished `ItemStack` at load time, and
`LootEntryParser` wraps it in a `LootItem`. Nothing downstream re-inspects the item to decide what to
build — a new item property is expressed entirely in the config's `item:` value. Only a durability
*range* is left to roll per drop; a single value is baked into the template. See `../lootgen/AGENTS.md`.

Because the template carries a `sellPrice` read from the price manager, `ItemPriceManager` must be
constructed **before** these configs load (`initializeEconomyComponents` before spawner settings).

## The rename that is not a migration

The spawner settings files were renamed in 1.8.0 and their contents are deliberately **not** carried
across: the new files ship in a format the old ones cannot express, so copying an old file would import
loot entries that no longer parse. `SupersededConfigNotice` just tells the operator, once, that the old
file (`spawners_settings.yml` / `item_spawners_settings.yml`) was left untouched on disk for them to
read by hand. It changes nothing. This is separate from the version-less config top-up in `updates/`.

## The owned-section trap

`SpawnerSettingsConfig.load()` marks every `*.loot` section and `custom_spawners` as
`YamlMigrator.OwnedSection`, so the shipped file's relabelled default entries are not added next to a
user's existing ones. Without it, `addMissingKeys` would silently double every drop. **Keep that
argument** if you touch the load call. `spawner_items.yml` has no migrator, so it only affects fresh installs. See `../../updates/AGENTS.md`.

## Gotchas

- `spawner_mobs.yml` / `spawner_items.yml` are the current files; `spawners_settings.yml` / `item_spawners_settings.yml` are the superseded names. Load code references the current ones as `RESOURCE`, the old ones as `LEGACY_RESOURCE`.
- Cage previews must be applied everywhere a spawner is materialized (item metadata, placed blocks, DB restore, reloads) via `SpawnerDisplayConfigurator`, or the preview silently loses entity NBT / item components.
- Any code that branches on spawner kind must handle item spawners (`EntityType.ITEM`); see `../AGENTS.md`.
