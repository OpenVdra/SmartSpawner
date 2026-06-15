---
title: Main Configuration
description: Detailed guide to configuring SmartSpawner plugin settings
---

This page explains the main `config.yml` file for SmartSpawner. Use it to tune language, spawner behavior, economy, visual effects, logging, database storage, and performance.

## Time Format Guide

SmartSpawner accepts short, readable duration values:

- **Simple formats**: `20s` (20 seconds), `5m` (5 minutes), `1h` (1 hour)
- **Complex format**: `1d_2h_30m_15s` (1 day, 2 hours, 30 minutes, 15 seconds)
- **Units**: `s` = seconds, `m` = minutes, `h` = hours, `d` = days, `w` = weeks, `mo` = months, `y` = years

## Adding Custom Language

1. Create a new folder in the `language` directory.
2. Copy the files from the `en_US` folder as a template.
3. Translate the language files:
   - `messages.yml`
   - `command_messages.yml`
   - `gui.yml`
   - `command_gui.yml`
   - `items.yml`
   - `formatting.yml`
   - `hologram.yml`
4. Set `language` in `config.yml` to your custom folder name.

## Language Settings

```yaml
language: en_US
gui_layout: default
debug: false
```

- `language`: Language folder to load from the plugin's `language` directory.
- `gui_layout`: GUI layout folder to load. Built-in options are `default`, `DonutSMP`, and `DonutSMP_v2`.
- `debug`: Enables extra console output for troubleshooting.

### Built-in Languages

| Locale | Language |
| --- | --- |
| `en_US` | English |
| `en_US_DonutSMP` | English, styled for the DonutSMP layout |
| `en_US_DonutSMP_v2` | English, styled for the DonutSMP v2 layout |
| `tr_TR` | Turkish |
| `vi_VN` | Vietnamese (Tiếng Việt) |

To use Vietnamese, set:

```yaml
language: vi_VN
```

The `language` and `gui_layout` settings are independent. For example, `vi_VN` can be used with the default GUI layout:

```yaml
language: vi_VN
gui_layout: default
```

## Core Spawner Properties

```yaml
spawner_properties:
  default:
    min_mobs: 1
    max_mobs: 4
    range: 16
    delay: 25s

    max_storage_pages: 1
    max_stored_exp: 1000
    max_stack_size: 10000

    allow_exp_mending: true
    protect_from_explosions: true
```

- `min_mobs` / `max_mobs`: Random mob count used for each generation cycle.
- `range`: Player activation distance in blocks.
- `delay`: Time between generation cycles.
- `max_storage_pages`: Internal storage size. Each page provides 45 slots.
- `max_stored_exp`: Maximum XP the spawner can store.
- `max_stack_size`: Maximum spawner amount in one stack.
- `allow_exp_mending`: Allows stored XP to repair items with Mending.
- `protect_from_explosions`: Prevents Smart Spawner blocks from being destroyed by explosions.

## Spawner Breaking Mechanics

```yaml
spawner_break:
  enabled: true
  direct_to_inventory: false
  required_tools:
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
  durability_loss: 1
  sneak_break: true
  sell_and_xp_break: true
  silk_touch:
    required: true
    level: 1
```

- `enabled`: Master switch for breaking and collecting Smart Spawners.
- `direct_to_inventory`: If `true`, collected spawners go directly into the player's inventory instead of dropping on the ground.
- `required_tools`: Tools that are allowed to break Smart Spawners.
- `durability_loss`: Durability removed from the tool per break.
- `sneak_break`: Allows sneaking while breaking a Smart Spawner stack to remove up to 64 spawners at once. If `false`, sneaking breaks only one spawner, the same as a normal break.
- `sell_and_xp_break`: When a Smart Spawner is fully removed, automatically sells stored items and claims remaining XP. This requires a sell integration and the `smartspawner.sellall` permission.
- `silk_touch.required`: Whether Silk Touch is required to collect the spawner.
- `silk_touch.level`: Minimum Silk Touch level required.

:::note[Drop Chance and Stack Breaking]
If `sneak_break` is enabled and a Smart Spawner entity has `drop_chance` configured in `spawners_settings.yml`, sneak stack breaking is blocked for that spawner. Players must break one spawner at a time so drop chance rolls cannot remove a large stack in one action.
:::

:::note[Drop Chance Bypass]
Players with `smartspawner.break.bypassdropchance` always receive spawner drops, can use sneak stack breaking, and can open the stacker GUI even when the spawner has `drop_chance` configured.
:::

## Natural/Vanilla Spawner Settings

```yaml
natural_spawner:
  breakable: false
  convert_to_smart_spawner: false
  # drop_chance:
  #   default: 50.0
  #   ZOMBIE: 75.0
  #   SKELETON: 50.0
  #   BLAZE: 25.0
  spawn_mobs: true
  protect_from_explosions: false
```

- `breakable`: Allows naturally generated vanilla spawners to be broken.
- `convert_to_smart_spawner`: If `true`, broken natural spawners become Smart Spawners. If `false`, they drop vanilla spawner items.
- `drop_chance`: Optional chance for broken natural spawners to drop a spawner item. Use the `default` key to set one chance for **all** spawner types at once, and add specific entity types only to override that default. If `default` is omitted, any unspecified entity falls back to `100.0`.
- `spawn_mobs`: Allows natural spawners to spawn mobs.
- `protect_from_explosions`: Protects natural spawner blocks from explosions.

## Storage Selling Settings

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  excellenteconomy_currency: coins
  price_source_mode: SHOP_PRIORITY
  shop_integration:
    enabled: true
    preferred_plugin: auto
  custom_prices:
    enabled: true
    default_price: 1.0
```

These settings configure the sell integration used by Smart Spawner storage.

- `enabled`: Enables selling items from spawner storage.
- `currency`: Economy backend. Supported values are `VAULT` and `EXCELLENTECONOMY`.
- `excellenteconomy_currency`: ExcellentEconomy currency name. Only used when `currency` is `EXCELLENTECONOMY`.
- `price_source_mode`: Selects where sell prices come from.
- `shop_integration.enabled`: Enables shop plugin price lookup.
- `shop_integration.preferred_plugin`: `auto`, `EconomyShopGUI`, `EconomyShopGUI-Premium`, `ShopGUIPlus`, or `zShop`.
- `custom_prices.enabled`: Enables custom prices from the configured price file.
- Custom prices are loaded from `item_prices.yml`.
- `custom_prices.default_price`: Fallback price for items without a custom price. Set to `0.0` to prevent selling unconfigured items.

### Price Source Modes

| Mode | Behavior |
| --- | --- |
| `SHOP_ONLY` | Uses only shop integration prices. Custom prices are ignored. |
| `SHOP_PRIORITY` | Uses shop prices first, then custom prices as fallback. Recommended for most servers using shop plugins. |
| `CUSTOM_ONLY` | Uses only prices from `item_prices.yml`. Shop prices are ignored. |
| `CUSTOM_PRIORITY` | Uses custom prices first, then shop prices as fallback. |

## Item Collection System

```yaml
hopper:
  enabled: false
  check_delay: 3s
  stack_per_transfer: 5
```

- `enabled`: Enables automatic item transfer through hoppers.
- `check_delay`: Time between hopper transfer checks.
- `stack_per_transfer`: Number of item stacks moved per transfer, up to 5.

## Bedrock Player Support

```yaml
bedrock_support:
  enable_formui: true
```

- `enable_formui`: Shows mobile-friendly form menus to Bedrock players instead of chest GUIs. Requires Floodgate.

## Visual Effects

```yaml
hologram:
  enabled: false
  offset_x: 0.5
  offset_y: 1.6
  offset_z: 0.5
  alignment: CENTER
  shadowed_text: true
  see_through: false
  transparent_background: false

particle:
  spawner_stack: true
  spawner_activate: true
  spawner_generate_loot: true
```

- `hologram.enabled`: Shows floating text above spawners.
- `offset_x`, `offset_y`, `offset_z`: Hologram position relative to the spawner block.
- `alignment`: Hologram text alignment. Supported values are `CENTER`, `LEFT`, and `RIGHT`.
- `shadowed_text`: Adds text shadow.
- `see_through`: Allows the hologram to be visible through blocks.
- `transparent_background`: Removes the hologram background.
- `particle.spawner_stack`: Shows particles when spawners are stacked.
- `particle.spawner_activate`: Shows particles when a spawner activates.
- `particle.spawner_generate_loot`: Shows particles when loot is generated.

## Spawner Action Logging

```yaml
logging:
  enabled: true
  json_format: false
  console_output: false
  max_log_files: 10
  max_log_size_mb: 10
  log_all_events: false
  logged_events:
    - SPAWNER_PLACE
    - SPAWNER_BREAK
    - SPAWNER_EXPLODE
    - SPAWNER_STACK_HAND
    - SPAWNER_STACK_GUI
    - SPAWNER_DESTACK_GUI
    - SPAWNER_EXP_CLAIM
    - SPAWNER_SELL_ALL
    - SPAWNER_ITEM_TAKE_ALL
    - SPAWNER_ITEMS_SORT
    - SPAWNER_ITEM_FILTER
    - SPAWNER_DROP_PAGE_ITEMS
    - COMMAND_EXECUTE_PLAYER
    - COMMAND_EXECUTE_CONSOLE
    - COMMAND_EXECUTE_RCON
```

- `enabled`: Enables file logging for spawner actions.
- `json_format`: If `false`, logs are human-readable. If `true`, logs are JSON.
- `console_output`: Also prints log entries to the console.
- `max_log_files`: Number of rotated log files to keep.
- `max_log_size_mb`: Maximum size of each log file before rotation.
- `log_all_events`: If `true`, logs every supported event and ignores `logged_events`.
- `logged_events`: Events to log when `log_all_events` is `false`.

### Available Log Events

| Event | Description |
| --- | --- |
| `SPAWNER_PLACE` | Spawner placed by a player |
| `SPAWNER_BREAK` | Spawner broken by a player |
| `SPAWNER_EXPLODE` | Spawner destroyed by an explosion |
| `SPAWNER_STACK_HAND` | Spawner stacked by hand |
| `SPAWNER_STACK_GUI` | Spawner stacked through the GUI |
| `SPAWNER_DESTACK_GUI` | Spawner destacked through the GUI |
| `SPAWNER_GUI_OPEN` | Main spawner GUI opened |
| `SPAWNER_STORAGE_OPEN` | Storage GUI opened |
| `SPAWNER_STACKER_OPEN` | Stacker GUI opened |
| `SPAWNER_EXP_CLAIM` | XP claimed from a spawner |
| `SPAWNER_SELL_ALL` | Items sold from a spawner |
| `SPAWNER_ITEM_TAKE_ALL` | All items taken from storage |
| `SPAWNER_ITEM_DROP` | Item dropped from storage with the drop key |
| `SPAWNER_ITEMS_SORT` | Items sorted in storage |
| `SPAWNER_ITEM_FILTER` | Item filter toggled in storage |
| `SPAWNER_DROP_PAGE_ITEMS` | All items on the current page dropped from storage |
| `SPAWNER_EGG_CHANGE` | Entity type changed with a spawn egg |
| `COMMAND_EXECUTE_PLAYER` | Command executed by a player |
| `COMMAND_EXECUTE_CONSOLE` | Command executed by console |
| `COMMAND_EXECUTE_RCON` | Command executed through RCON |

## Database Settings

```yaml
database:
  mode: YAML
  server_name: "server1"
  sync_across_servers: false
  migrate_from_local: true
  database: "smartspawner"
  sqlite:
    file: "spawners.db"
  sql:
    host: "localhost"
    port: 3306
    username: "root"
    password: ""
    pool:
      maximum-size: 10
      minimum-idle: 2
      connection-timeout: 10000
      max-lifetime: 1800000
      idle-timeout: 600000
      keepalive-time: 30000
      leak-detection-threshold: 0
```

- `mode`: Storage backend. Supported values are `YAML`, `MYSQL`, and `SQLITE`.
- `server_name`: Unique server name used for cross-server database setups.
- `sync_across_servers`: Shows a server selection page in `/smartspawner list` so admins can view spawners from all servers in a shared MySQL database. Only works with `MYSQL`.
- `migrate_from_local`: Automatically migrates local data on startup. `spawners_data.yml` can migrate to MySQL or SQLite, and `spawners.db` can migrate to MySQL. Migrated files are renamed with a `.migrated` suffix.
- `database`: MySQL/MariaDB database name.
- `sqlite.file`: SQLite database filename stored in the plugin data folder.
- `sql.host`, `sql.port`, `sql.username`, `sql.password`: MySQL/MariaDB connection details.
- `sql.pool.maximum-size`: Maximum database connections in the pool.
- `sql.pool.minimum-idle`: Minimum idle connections to keep ready.
- `sql.pool.connection-timeout`: Maximum time in milliseconds to wait for a connection.
- `sql.pool.max-lifetime`: Maximum lifetime of one connection in milliseconds.
- `sql.pool.idle-timeout`: Maximum idle time before a connection can be removed.
- `sql.pool.keepalive-time`: Interval in milliseconds for keepalive checks. Set to `0` to disable.
- `sql.pool.leak-detection-threshold`: Time in milliseconds before HikariCP logs a possible connection leak. Set to `0` to disable.

## Performance Settings

```yaml
performance:
  loot_generation:
    approximate_loot: true
    approximation_threshold: 1000
```

These settings control how SmartSpawner calculates drops for large stacked spawners.

### approximate_loot

`approximate_loot` decides whether SmartSpawner may use a faster calculation when one spawn cycle represents a very large number of mobs.

- `true`: Recommended for most servers. SmartSpawner rolls loot exactly for normal-sized batches. When the batch becomes extremely large, it switches to a fast average-based calculation with a small random variance. This keeps large stacks from using too much CPU while still producing realistic totals over time.
- `false`: SmartSpawner always rolls loot exactly, one mob at a time. This keeps the closest possible per-mob randomness, but very large stacks can take more CPU during loot generation.

XP is not approximated by this setting. XP is calculated from the generated mob count and the entity XP value.

### approximation_threshold

`approximation_threshold` controls how soon approximation mode starts when `approximate_loot` is `true`.

- Lower values switch to the faster calculation earlier. This is better for performance, especially on servers with very large spawner stacks.
- Higher values keep exact rolling for longer. This is closer to per-mob randomness, but costs more CPU for huge batches.

Approximation only applies per loot item when the generated mob count is large enough for that item's drop chance:

```text
mobCount > (97.5 / dropChancePercent) * approximation_threshold
```

Examples with `approximation_threshold: 1000`:

| Drop Chance | Approximation Starts Around |
| --- | --- |
| `10%` | `9,750` mobs |
| `1%` | `97,500` mobs |
| `0.1%` | `975,000` mobs |

Recommended values:

| Value | Behavior |
| --- | --- |
| `10-100` | Very aggressive optimization for massive stacks |
| `100-1000` | Balanced performance and accuracy |
| `1000-10000` | Conservative, closer to exact rolling |

<br>
<br>

---

*Last update: June 14, 2026*
