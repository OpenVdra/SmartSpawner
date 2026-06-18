# Spawner Settings

The `spawners_settings.yml` file in `plugins/SmartSpawner/` controls the drop tables, XP values, head textures, and optional drop chances for each mob type used by Smart Spawners.

::: info Drop Multiplier
Each generation cycle rolls drops between **min_mobs** and **max_mobs** times (default: 1–4). The configured amounts are base values per mob; actual output is higher.
:::

::: warning Current Limitations
Smart Spawners do not support potions or enchanted books. Only **tipped arrows** are supported with potion effects.
:::

## Configuration Format

```yaml
# Global fallback material for unknown mob types
default_material: "SPAWNER"

MOB_NAME:
  experience: <number>
  drop_chance: <percentage>   # Optional — defaults to 100.0 when omitted
  head_texture:
    material: <MATERIAL>
    custom_texture: <hash>    # null for vanilla heads
  loot:                       # Optional
    ITEM_ID:
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Optional — for tools/weapons
      potion_type: <TYPE>     # Optional — for tipped arrows only
```

## Properties Reference

### Spawner-Level Properties

| Property | Format | Description |
|----------|--------|-------------|
| `experience` | `5` | XP generated per spawner trigger |
| `drop_chance` | `75.0` | Chance the Smart Spawner item drops when broken. Omit to use 100.0. |
| `material` | `"PLAYER_HEAD"` | Head material displayed in the spawner block |
| `custom_texture` | `"abc123..."` | Base64 texture hash for player heads. Use `null` for vanilla heads. |

### Loot Properties

| Property | Format | Description |
|----------|--------|-------------|
| `amount` | `1-3` | Item quantity range per generation cycle |
| `chance` | `50.0` | Drop probability (0.0–100.0) |
| `durability` | `1-384` | Durability range for tools/weapons |
| `potion_type` | `POISON` | Potion type for tipped arrows |

## Spawner Break Drop Chance

The `drop_chance` property controls whether the **spawner item itself** drops when that spawner is broken. This is independent of the loot `chance` which controls generated drops.

- If `drop_chance` is **omitted**, the spawner always drops (100% chance).
- If `drop_chance` is set, each break has that percentage chance of returning the spawner item.
- When `sneak_break` is enabled, spawners with `drop_chance` configured **cannot** be sneak-broken as a stack; players must break one at a time.
- Players with `smartspawner.break.bypassdropchance` always receive the drop and can use all stacking features.

## Examples

### Mob with Custom Head

```yaml
# Reference: https://minecraft.wiki/w/Cow#Drops
COW:
  experience: 3
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3"
  loot:
    LEATHER:
      amount: 0-2
      chance: 66.67
    BEEF:
      amount: 1-3
      chance: 100.0
```

### Mob with Vanilla Head

```yaml
# Reference: https://minecraft.wiki/w/Skeleton#Drops
SKELETON:
  experience: 5
  head_texture:
    material: "SKELETON_SKULL"
    custom_texture: null
  loot:
    BONE:
      amount: 0-2
      chance: 66.67
    ARROW:
      amount: 0-2
      chance: 66.67
    BOW:
      amount: 1-1
      chance: 8.5
      durability: 1-384
```

### Mob with Weapons

```yaml
# Reference: https://minecraft.wiki/w/Wither_Skeleton#Drops
WITHER_SKELETON:
  experience: 5
  head_texture:
    material: "WITHER_SKELETON_SKULL"
    custom_texture: null
  loot:
    COAL:
      amount: 0-1
      chance: 33.33
    BONE:
      amount: 0-2
      chance: 66.67
    WITHER_SKELETON_SKULL:
      amount: 0-1
      chance: 2.5
    STONE_SWORD:
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob with Tipped Arrows

```yaml
# Reference: https://minecraft.wiki/w/Bogged#Drops
BOGGED:
  experience: 5
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
  loot:
    BONE:
      amount: 0-2
      chance: 66.67
    TIPPED_ARROW:
      amount: 0-2
      chance: 50.0
      potion_type: POISON
```

### Mob with Drop Chance

```yaml
ALLAY:
  experience: 0
  drop_chance: 75.0   # 75% chance to drop spawner when broken
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
```

### Mob with No Drops

```yaml
# Reference: https://minecraft.wiki/w/Bat#Drops
BAT:
  experience: 0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "81c5cc1f40005a33124c60384a0f17a36a7b19ae90f1c32dcda17b5b56280a43"
  # No loot section = no item drops
```

## Drop Mechanics

Actual drops per generation cycle are calculated as:

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

With defaults (`min_mobs=1`, `max_mobs=4`):

| Config amount | Possible output |
|---------------|-----------------|
| `1-1` | 1–4 items |
| `2-3` | 2–12 items |
| `1-2` | 1–8 items |

Each loot entry is rolled independently. A single cycle can produce multiple item types simultaneously.

## Finding Head Textures

Custom player head textures can be found at:
- [Minecraft-Heads.com](https://minecraft-heads.com/)
- [MCHeads.net](https://mc-heads.net/)

Use the hash portion of the texture URL only (without `http://textures.minecraft.net/texture/`).

### Vanilla Head Materials

Some mobs use built-in skull types with `custom_texture: null`:
- `SKELETON_SKULL`
- `WITHER_SKELETON_SKULL`
- `ZOMBIE_HEAD`
- `PIGLIN_HEAD`
- `DRAGON_HEAD`

## Default Configuration

SmartSpawner ships with a comprehensive default `spawners_settings.yml` covering all vanilla mob types with accurate drop tables based on [Minecraft Wiki](https://minecraft.wiki) data.

- **View online:** [GitHub: spawners_settings.yml](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawners_settings.yml)
- **Reset:** Delete `spawners_settings.yml` and restart the server to regenerate it.

## Give Spawners

```bash
/ss give spawner <player> <mob_type> [amount]
```

Examples:
```bash
/ss give spawner @p skeleton 1
/ss give spawner Player123 wither_skeleton 3
```
