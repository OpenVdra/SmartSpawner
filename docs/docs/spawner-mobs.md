# Mob Spawners

The `spawner_mobs.yml` file in `plugins/SmartSpawner/` controls the drops, XP, menu head and drop chance of every mob spawner.

Every mob has one spawner under its own name, such as `zombie`. Extra spawners for the same mob, each with its own loot, go under `custom_spawners`.

## In-game Editing

Use `/ss editloot <name>` to change a spawner's loot without opening the file. It works for both mob spawners and custom spawners, for example `/ss editloot zombie` or `/ss editloot golden_zombie`. See [Commands](/docs/commands#ss-editloot) for how the editor works.

::: info Drop Multiplier
Each generation cycle rolls drops between **min_mobs** and **max_mobs** times (default: 1–4). The configured amounts are base values per mob; actual output is higher.
:::

## Configuration Format

```yaml
zombie:                       # The mob's own spawner, named after the mob
  experience: <number>
  drop_chance: <percentage>   # Optional, defaults to 100.0
  nbt_data: <summon-style SNBT> # Optional
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>      # null for vanilla heads
  loot:                       # Optional
    1:
      item: <item>            # Required
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Optional, for tools and weapons

custom_spawners:
  golden_zombie:              # Any name that is not a mob name
    entity: ZOMBIE            # Required
    display_name: Golden Zombie # Optional
    loot:
      1:
        item: GOLD_INGOT
        amount: 1-2
        chance: 50.0
```

## Custom Spawners

A custom spawner is a second spawner for a mob that already has one, with its own loot. Players see it as a separate spawner: it has its own name, it cannot be stacked with the normal spawner of that mob, and `/ss give` gives it by its own name.

- `entity` is required and names the mob.
- `display_name` replaces the mob name everywhere players see this spawner: the item name, menu titles and the hologram. Use plain text, the colors come from the language files.
- `experience`, `drop_chance`, `nbt_data` and `mob_head` are optional. Anything left out is taken from the mob's own spawner.
- `loot` is never taken from the mob's spawner. A custom spawner without `loot` drops nothing.
- The name must not be a mob name, and must be unique across `spawner_mobs.yml` and `spawner_items.yml`. The console warns about any name that breaks these rules.

### Renaming a Custom Spawner

Spawners already placed in the world remember the name they were given. To rename a custom spawner and keep the ones already placed, list the old name under `aliases`:

```yaml
custom_spawners:
  golden_zombie:
    entity: ZOMBIE
    aliases: [lucky_zombie]
    loot: ...
```

Placed spawners and spawner items with the old name then belong to `golden_zombie`. They stack with new ones, and placed spawners switch to the new name on their own.

If a custom spawner is removed instead, its placed spawners work as the mob's own spawner and the console names the missing spawner once. Adding the entry back restores them.

## Naming an Item

Each loot entry names its item in the `item` field. It is required: an entry without one is skipped
and reported in the console.

`item` accepts three things:

| Form | Example | Use it for |
|------|---------|------------|
| A material name | `ARROW` | Plain items |
| A `/give` item string | `tipped_arrow[potion_contents={potion:"minecraft:poison"}]` | Potions, enchanted items, named items, anything with extra data |
| `nbt:` plus a code | `nbt:H4sIAAAA...` | Items copied out of the game exactly as they are |

The second form is the same text the `/give` command completes for you in game. Build the item you
want with `/give`, copy the part after the player name, and paste it here between single quotes.

Entries are numbered, and the number is only a position in the list. The `item` line is what says
what drops, which is why the same material can appear more than once:

```yaml
bogged:
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 0-1
      chance: 50.0
    2:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:slowness"}]'
      amount: 0-1
      chance: 10.0
```

An entry the server cannot read is skipped and reported in the console with the spawner and entry name.
The rest of the file still loads.

## Properties Reference

### Spawner-Level Properties

| Property | Format | Description |
|----------|--------|-------------|
| `entity` | `ZOMBIE` | The mob of a custom spawner. Only used under `custom_spawners`. |
| `display_name` | `Golden Zombie` | Name players see instead of the mob name. |
| `aliases` | `[lucky_zombie]` | Old names of a renamed custom spawner. |
| `experience` | `5` | XP generated per spawner trigger |
| `nbt_data` | `{profile:DrDonutt}` | Summon-style SNBT used by the rotating entity model inside the spawner cage |
| `drop_chance` | `75.0` | Chance the Smart Spawner item drops when broken. Omit to use 100.0. |
| `mob_head.item` | `"PLAYER_HEAD"` | Head shown for this spawner in menus |
| `mob_head.hash_texture` | `"abc123..."` | Texture hash for player heads. Use `null` for vanilla heads. |

### Loot Properties

| Property | Format | Description |
|----------|--------|-------------|
| `item` | `ARROW` | The item that drops |
| `amount` | `1-3` | Item quantity range per generation cycle |
| `chance` | `50.0` | Drop probability (0.0 to 100.0) |
| `durability` | `1-384` | Durability range for tools and weapons. A single value like `100` is also accepted. |

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
cow:
  experience: 3
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3"
  loot:
    1:
      item: LEATHER
      amount: 0-2
      chance: 66.67
    2:
      item: BEEF
      amount: 1-3
      chance: 100.0
```

### Mob with Weapons

```yaml
# Reference: https://minecraft.wiki/w/Wither_Skeleton#Drops
wither_skeleton:
  experience: 5
  mob_head:
    item: "WITHER_SKELETON_SKULL"
    hash_texture: null
  loot:
    1:
      item: COAL
      amount: 0-1
      chance: 33.33
    2:
      item: BONE
      amount: 0-2
      chance: 66.67
    3:
      item: STONE_SWORD
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob with Potions and Enchanted Gear

```yaml
witch:
  experience: 5
  loot:
    1:
      item: 'potion[potion_contents={potion:"minecraft:strength"}]'
      amount: 0-1
      chance: 5.0
    2:
      item: 'diamond_sword[enchantments={"minecraft:sharpness":5}]'
      amount: 1-1
      chance: 0.5
```

### Mob with Drop Chance

```yaml
allay:
  experience: 0
  drop_chance: 75.0   # 75% chance to drop spawner when broken
```

### Mob with No Drops

```yaml
# Reference: https://minecraft.wiki/w/Bat#Drops
bat:
  experience: 0
  # No loot section = no item drops
```

### Two Spawners for the Same Mob

The normal zombie spawner, plus a rarer one that drops gold and uses a different head. The custom one keeps the zombie's XP because it leaves `experience` out.

```yaml
zombie:
  experience: 5
  loot:
    1:
      item: ROTTEN_FLESH
      amount: 0-2
      chance: 100.0

custom_spawners:
  golden_zombie:
    entity: ZOMBIE
    display_name: Golden Zombie
    mob_head:
      item: GOLD_BLOCK
    loot:
      1:
        item: GOLD_INGOT
        amount: 1-2
        chance: 50.0
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

Some mobs use built-in skull types with `hash_texture: null`:
- `SKELETON_SKULL`
- `WITHER_SKELETON_SKULL`
- `ZOMBIE_HEAD`
- `PIGLIN_HEAD`
- `DRAGON_HEAD`

## Default Configuration

SmartSpawner ships with a default `spawner_mobs.yml` covering all vanilla mob types with drop tables based on [Minecraft Wiki](https://minecraft.wiki) data.

- **View online:** [GitHub: spawner_mobs.yml](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawner_mobs.yml)
- **Reset:** Delete `spawner_mobs.yml` and restart the server to regenerate it.

::: info Upgrading from 1.8
A `spawner_mobs.yml` in the 1.8 layout is converted on the first start. Entries named like `zombie_spawner` become `zombie`, other entries move to `custom_spawners`, and the old file is kept as `spawner_mobs.yml.1.8-backup`. Placed spawners and spawner items keep working.
:::

## Give Spawners

```bash
/ss give <player> <name> [amount]
```

Examples:
```bash
/ss give Steve skeleton 1
/ss give Player123 golden_zombie 3
```
