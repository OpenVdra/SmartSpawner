# Item Spawner Settings

The `item_spawners_settings.yml` file in `plugins/SmartSpawner/` configures drop tables, XP values, and textures for **Item Spawners**, the spawner type that generates raw materials instead of mob drops.

::: info Drop Multiplier
Each cycle generates drops between **min_mobs** and **max_mobs** times (default: 1–4). Configured amounts are base values that get multiplied.
:::

::: warning Limitations
Item spawners do not support potions or enchanted books. Only **tipped arrows** are supported with potion effects.
:::

## Configuration Format

```yaml
# Global fallback for unknown item types
default_material: "SPAWNER"

ITEM_MATERIAL:
  material: <MATERIAL>
  experience: <number>
  loot:
    ITEM_ID:
      amount: <min>-<max>
      chance: <percentage>
      potion_type: <TYPE>   # Optional — tipped arrows only
  head_texture:
    material: <MATERIAL>
    custom_texture: <hash>  # null for vanilla materials
```

## Properties Reference

| Property | Format | Description |
|----------|--------|-------------|
| `material` | `"DIAMOND"` | Primary material this spawner represents |
| `experience` | `1` | XP generated per spawner trigger |
| `amount` | `1-1` | Base item quantity range per cycle |
| `chance` | `100.0` | Drop probability (0.0–100.0) |
| `potion_type` | `POISON` | Potion type for tipped arrows only |

## Examples

### Basic Resource Spawner

```yaml
DIAMOND:
  material: "DIAMOND"
  experience: 1
  loot:
    DIAMOND:
      amount: 1-1
      chance: 100.0
  head_texture:
    material: "DIAMOND"
    custom_texture: null
```

### Multiple Drop Types

```yaml
GOLD_INGOT:
  material: "GOLD_INGOT"
  experience: 1
  loot:
    GOLD_INGOT:
      amount: 1-2
      chance: 100.0
    GOLD_NUGGET:
      amount: 3-5
      chance: 50.0
  head_texture:
    material: "GOLD_INGOT"
    custom_texture: null
```

### Custom Head Texture

```yaml
EMERALD:
  material: "EMERALD"
  experience: 1
  loot:
    EMERALD:
      amount: 1-1
      chance: 100.0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "abc123def456..."
```

### Tipped Arrow Spawner

```yaml
TIPPED_ARROW:
  material: "TIPPED_ARROW"
  experience: 1
  loot:
    TIPPED_ARROW:
      amount: 8-16
      chance: 100.0
      potion_type: POISON
  head_texture:
    material: "TIPPED_ARROW"
    custom_texture: null
```

### Rare Item with Chance Drop

```yaml
TOTEM_OF_UNDYING:
  material: "TOTEM_OF_UNDYING"
  experience: 2
  loot:
    TOTEM_OF_UNDYING:
      amount: 1-1
      chance: 75.0
    EMERALD:
      amount: 1-3
      chance: 50.0
  head_texture:
    material: "TOTEM_OF_UNDYING"
    custom_texture: null
```

## Drop Mechanics

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

With defaults (`min_mobs=1`, `max_mobs=4`):

| Config amount | Possible output |
|---------------|-----------------|
| `1-1` | 1–4 items |
| `1-2` | 1–8 items |
| `2-3` | 2–12 items |

## Default Configuration

SmartSpawner ships with defaults for common valuable materials.

- **View online:** [GitHub: item_spawners_settings.yml](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/item_spawners_settings.yml)
- **Reset:** Delete the file and restart to regenerate it.

## Give Item Spawners

```bash
/ss give item_spawner <player> <MATERIAL> [amount]
```

Examples:
```bash
/ss give item_spawner @p DIAMOND 1
/ss give item_spawner Player123 NETHERITE_INGOT 5
```
