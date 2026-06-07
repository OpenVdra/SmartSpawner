---
title: Commands Reference
description: Complete command reference for SmartSpawner.
---

## Command Syntax

All commands can be used with these aliases:
- `/ss`
- `/spawner` 
- `/smartspawner`

## Core Commands

### Administrative Commands

| Command | Permission |
|---------|-------------|
| `/ss give spawner <player> <type> [amount]` | `smartspawner.command.give` |
| `/ss give vanilla_spawner <player> <type> [amount]` | `smartspawner.command.give` |
| `/ss give item_spawner <player> <item_type> [amount]` | `smartspawner.command.give` |
| `/ss hologram` | `smartspawner.command.hologram` |
| `/ss list` | `smartspawner.command.list` |
| `/ss prices` | `smartspawner.command.prices` |
| `/ss reload` | `smartspawner.command.reload` |
| `/ss clear holograms` | `smartspawner.command.clear` |
| `/ss clear ghost_spawners` | `smartspawner.command.clear` |
| `/ss near [radius]` | `smartspawner.command.near` |
| `/ss near cancel` | `smartspawner.command.near` |
| `/ss set <stack_size|range|delay> <value> [world x y z]` | `smartspawner.command.set` |
| `/ss language [locale]` | `smartspawner.command.language` |
| `/ss gui_layout [layout]` | `smartspawner.command.gui_layout` |

## Command Details

### `/ss give spawner`

```bash
/ss give spawner <player> <type> [amount]
```

Give smart spawners to a player.

**About SmartSpawners:**
- **GUI Interface**: Right-click to access spawner GUI
- **No Mob Spawning**: Generates drops and experience without spawning actual mobs
- **Stackable**: Multiple spawners can be stacked in a single block for increased efficiency
- **Performance Optimized**: Reduces server lag by eliminating entity spawning

**Parameters:**
- `<player>` - Target player (supports player selectors like `@p`, `@a`, etc.)
- `<type>` - Entity type (zombie, skeleton, blaze, etc.)
- `[amount]` - Optional quantity (1-6400, defaults to 1 if not specified)

### `/ss give vanilla_spawner`

```bash
/ss give vanilla_spawner <player> <type> [amount]
```

Give vanilla spawners to a player.

**About Vanilla Spawners:**
- **Traditional Behavior**: Functions exactly like standard Minecraft spawners
- **Entity Spawning**: Spawns actual mobs that can move, attack, and interact
- **No Stacking**: Each spawner operates independently and cannot be combined
- **No GUI**: Standard Minecraft spawner mechanics without additional interface

**Parameters:**
- `<player>` - Target player (supports player selectors like `@p`, `@a`, etc.)
- `<type>` - Entity type (zombie, skeleton, blaze, etc.)
- `[amount]` - Optional quantity (1-6400, defaults to 1 if not specified)

**Supported Entity Types:**
All vanilla Minecraft entities are supported. The command provides auto-completion suggestions as you type.

### `/ss give item_spawner`

```bash
/ss give item_spawner <player> <item_type> [amount]
```

Give item spawners to a player.

**About Item Spawners:**
- **Resource Generation**: Generates items directly without spawning mobs
- **Appearance**: Mob spinning inside replaced with an item spinning representing the spawner type
- **GUI Interface**: Right-click to access spawner GUI (similar to smart spawners)
- **Stackable**: Multiple item spawners can be stacked for increased efficiency
- **Configurable**: Each item type has configurable drops and experience in `item_spawners_settings.yml`

**Parameters:**
- `<player>` - Target player (supports player selectors like `@p`, `@a`, etc.)
- `<item_type>` - Material type (DIAMOND, NETHERITE_INGOT, EMERALD, etc.)
- `[amount]` - Optional quantity (1-6400, defaults to 1 if not specified)

**Configuration:**
See [Item Spawner Settings](/item_spawners_settings) for details on configuring item spawners.

### `/ss hologram`

Toggle the spawner hologram display for all spawners.

### `/ss list`

Administrative interface for spawner management.

**Features:**
- View all server spawners
- Teleport to locations
- Filter by world/status
- Real-time statistics

### `/ss prices`

Opens a GUI displaying sell prices for all spawner-generated items.

**Features:**
- **Interactive GUI**: Browse through paginated price listings
- **Real-time Prices**: Shows current shop/custom prices for all items
- **Integration Required**: Only available when sell integration is active

### `/ss reload`

Reload all configuration files without server restart.

**Reloads:**
- Main configuration (`config.yml`)
- Mob drops (`mob_drops.yml`)
- Item prices (`item_prices.yml`)
- Language files
- Hook integrations

### `/ss language`

```bash
/ss language
/ss language <locale>
```

View or change the active language. The `<locale>` argument autocompletes language folders from `plugins/SmartSpawner/language`, including custom folders added by the server owner.

### `/ss gui_layout`

```bash
/ss gui_layout
/ss gui_layout <layout>
```

View or change the active GUI layout. The `<layout>` argument autocompletes layout folders from `plugins/SmartSpawner/gui_layouts`, including custom folders added by the server owner.

### `/ss clear holograms`

Kill all text display holograms from the server.

**Use Cases:**
- Removing stuck or glitched holograms
- Resetting hologram system

**Features:**
- Removes all SmartSpawner text display entities
- Safe operation that only affects hologram entities
- Instant cleanup without server restart

### `/ss clear ghost_spawners`

Automatically detects and removes broken spawners without physical blocks.

**Use Cases:**
- Cleaning up database entries for spawners that no longer exist
- Fixing spawner data corruption
- Routine maintenance to keep spawner data clean

### `/ss near`

```bash
/ss near [radius]
/ss near cancel
```

Scan for nearby spawners and highlight them through walls using glowing block outlines.

**Features:**
- **Async Scan**: Non-blocking scan that won't lag the server. Progress is shown in a boss bar
- **Through-Wall Visibility**: BlockDisplay glow outlines are visible through any block
- **Player-Only**: Highlights are exclusive to the player who ran the command
- **Auto-Expire**: Highlights disappear automatically after 30 seconds
- **Cancellable**: Run `/ss near cancel` to remove highlights immediately

**Parameters:**
- `[radius]` — Scan radius in blocks (1–200, defaults to 50)

**Notes:**
- Maximum 200 spawners can be highlighted per scan
- Scans are optimised for servers with large numbers of spawners

### `/ss set`

```bash
/ss set <stack_size|range|delay> <value>
/ss set <stack_size|range|delay> <value> <world> <x> <y> <z>
```

Set a SmartSpawner property. Without coordinates, the command updates the spawner the player is looking at. With coordinates, it updates the SmartSpawner data at that exact block location.

**Parameters:**
- `<stack_size|range|delay>` - Property to update
- `<value>` - New value. `delay` accepts ticks or time formats such as `25s`, `1m`, or `1h`
- `[world x y z]` - Optional exact spawner location

<br>
<br>

<br>
<br>

---

*Last update: June 2, 2026*
