# Mob Spawners

The `spawner_mobs.yml` file in `plugins/SmartSpawner/` controls the drop tables, XP values, head textures, and optional drop chances for each mob type used by Smart Spawners.

## In-game Management

Use `/ss edit smartspawner` to edit existing mob entries. This editor is separate from the Item
Spawner editor and has no switch button. To create an entry, use:

```bash
/ss add smartspawner <mob> [name] [NBT tag]
```

The mob argument tab-completes living, spawnable entity types from the current server version and
accepts namespaced IDs such as `minecraft:zombie`. The name is optional and defaults to a value such
as `zombie_spawner`; spaces are converted to underscores. NBT is also optional and defaults to `{}`. When used,
it is a summon-style SNBT compound and must include its outer braces:

```bash
/ss add smartspawner zombie {}
/ss add smartspawner minecraft:zombie
/ss add smartspawner zombie Boss Room {NoAI:1b,Silent:1b}
```

SmartSpawner validates the tag without spawning an entity and stores it as `nbt_data`. The tagged
entity is rendered as the rotating model inside the spawner cage; the plugin still generates virtual
loot and does not spawn that entity into the world. Existing entries are never overwritten.

The loot screen has 27 slots and no pagination or navigation items. One lime stained-glass pane is
shown immediately after the last configured loot item; click it to add an item, and it moves one slot forward.

::: info Drop Multiplier
Each generation cycle rolls drops between **min_mobs** and **max_mobs** times (default: 1–4). The configured amounts are base values per mob; actual output is higher.
:::

## Configuration Format

```yaml
custom_spawner_name:
  entity: MOB_NAME          # Required; controls the entity type
  experience: <number>
  nbt_data: <summon-style SNBT> # Present on entries created with /ss add
  drop_chance: <percentage>   # Optional, defaults to 100.0 when omitted
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>    # null for vanilla heads
  loot:                       # Optional
    1:
      item: <item>            # Required
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Optional, for tools and weapons
```

## Naming an Item

Each loot entry names its item in the `item` field. It is required: an entry without one is skipped
and reported in the console.

`item` accepts four things:

| Form | Example | Use it for |
|------|---------|------------|
| A material name | `ARROW` | Plain items |
| A `/give` item string | `tipped_arrow[potion_contents={potion:"minecraft:poison"}]` | Potions, enchanted items, named items, anything with extra data |
| `nbt:` plus a code | `nbt:H4sIAAAA...` | Items copied out of the game exactly as they are |
| A plugin name, a colon, and that plugin's own item id | `mmoitems:SWORD:CUTLASS` | Items owned by another plugin |

The second form is the same text the `/give` command completes for you in game. Build the item you
want with `/give`, copy the part after the player name, and paste it here between single quotes.

The fourth form starts with the name of the plugin that owns the item; everything after the first
colon is that plugin's own syntax, which is why an MMOItems value carries two colons. Only
[MMOItems](/docs/integrations/mmoitems) is supported so far, and the entry is skipped with a console
message when the plugin is not installed. `minecraft:` is not a plugin name: `minecraft:arrow` is
read as the vanilla material, and `nbt` can never be a plugin name.

Entries are numbered, and the number is only a position in the list. The `item` line is what says
what drops, which is why the same material can appear more than once:

```yaml
poison_bogged_spawner:
  entity: BOGGED
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

An entry the server cannot read is skipped and reported in the console with the mob and entry name.
The rest of the file still loads.

## Properties Reference

### Spawner-Level Properties

| Property | Format | Description |
|----------|--------|-------------|
| `entity` | `ZOMBIE` | Entity type used by this named spawner entry |
| `experience` | `5` | XP generated per spawner trigger |
| `nbt_data` | `{profile:DrDonutt}` | Summon-style SNBT used by the rotating entity model inside the spawner cage |
| `drop_chance` | `75.0` | Chance the Smart Spawner item drops when broken. Omit to use 100.0. |
| `material` | `"PLAYER_HEAD"` | Head material displayed in the spawner block |
| `hash_texture` | `"abc123..."` | Base64 texture hash for player heads. Use `null` for vanilla heads. |

### Loot Properties

| Property | Format | Description |
|----------|--------|-------------|
| `item` | `ARROW` | The item that drops. Omit it to use the entry name. |
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
cow_spawner:
  entity: COW
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

### Mob with Vanilla Head

```yaml
# Reference: https://minecraft.wiki/w/Skeleton#Drops
skeleton_spawner:
  entity: SKELETON
  experience: 5
  mob_head:
    item: "SKELETON_SKULL"
    hash_texture: null
  loot:
    1:
      item: BONE
      amount: 0-2
      chance: 66.67
    2:
      item: ARROW
      amount: 0-2
      chance: 66.67
    3:
      item: BOW
      amount: 1-1
      chance: 8.5
      durability: 1-384
```

### Mob with Weapons

```yaml
# Reference: https://minecraft.wiki/w/Wither_Skeleton#Drops
wither_skeleton_spawner:
  entity: WITHER_SKELETON
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
      item: WITHER_SKELETON_SKULL
      amount: 0-1
      chance: 2.5
    4:
      item: STONE_SWORD
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob with Tipped Arrows

```yaml
# Reference: https://minecraft.wiki/w/Bogged#Drops
bogged_spawner:
  entity: BOGGED
  experience: 5
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
  loot:
    1:
      item: BONE
      amount: 0-2
      chance: 66.67
    2:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 0-2
      chance: 50.0
```

### Mob with Potions and Enchanted Gear

```yaml
witch_spawner:
  entity: WITCH
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
allay_spawner:
  entity: ALLAY
  experience: 0
  drop_chance: 75.0   # 75% chance to drop spawner when broken
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
```

### Mob with No Drops

```yaml
# Reference: https://minecraft.wiki/w/Bat#Drops
bat_spawner:
  entity: BAT
  experience: 0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "81c5cc1f40005a33124c60384a0f17a36a7b19ae90f1c32dcda17b5b56280a43"
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

Some mobs use built-in skull types with `hash_texture: null`:
- `SKELETON_SKULL`
- `WITHER_SKELETON_SKULL`
- `ZOMBIE_HEAD`
- `PIGLIN_HEAD`
- `DRAGON_HEAD`

## Default Configuration

SmartSpawner ships with a comprehensive default `spawner_mobs.yml` covering all vanilla mob types with accurate drop tables based on [Minecraft Wiki](https://minecraft.wiki) data.

- **View online:** [GitHub: spawner_mobs.yml](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawner_mobs.yml)
- **Reset:** Delete `spawner_mobs.yml` and restart the server to regenerate it.

## Give Spawners

```bash
/ss give <player> smart_spawner <name> [amount]
```

Examples:
```bash
/ss give Steve smart_spawner skeleton_spawner 1
/ss give Player123 smart_spawner wither_skeleton_spawner 3
```
