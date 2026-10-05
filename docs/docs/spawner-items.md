# Item Spawners

The `spawner_items.yml` file in `plugins/SmartSpawner/` configures drop tables, XP values, and textures for **Item Spawners**, the spawner type that generates raw materials instead of mob drops.

Every item spawner sits under the name of its item, such as `diamond`. Extra spawners for the same item, each with its own loot, go under `custom_spawners`.

## In-game Editing

Use `/ss editloot <name>` to change an item spawner's loot without opening the file, for example `/ss editloot diamond`. See [Commands](/docs/commands#ss-editloot) for how the editor works.

::: info Drop Multiplier
Each cycle generates drops between **min_mobs** and **max_mobs** times (default: 1–4). Configured amounts are base values that get multiplied.
:::

::: warning Limitations
Item spawners do not support potions or enchanted books. Only **tipped arrows** are supported with potion effects.
:::

## Configuration Format

```yaml
diamond:                    # The item's own spawner, named after the item
  experience: <number>
  nbt_data: <item>          # Optional, the item shown inside the spawner cage
  loot:
    1:
      item: <item>          # Required
      amount: <min>-<max>
      chance: <percentage>
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>    # null for vanilla materials

custom_spawners:
  rich_diamond:             # Any name that is not an item name
    item: DIAMOND           # Required
    display_name: Rich Diamond # Optional
    loot:
      1:
        item: DIAMOND_BLOCK
        amount: 1-1
        chance: 10.0
```

## Custom Spawners

Custom item spawners work the same way as [custom mob spawners](/docs/spawner-mobs#custom-spawners):

- `item` is required and names the item.
- `display_name` replaces the item name everywhere players see this spawner.
- `experience`, `nbt_data` and `mob_head` are optional. Anything left out is taken from the item's own spawner.
- `loot` is never taken from the item's spawner. A custom spawner without `loot` drops nothing.
- To rename one and keep the spawners already placed, list the old name under `aliases`.

## Properties Reference

| Property | Format | Description |
|----------|--------|-------------|
| `item` (spawner level) | `DIAMOND` | The item of a custom spawner. Only used under `custom_spawners`. |
| `display_name` | `Rich Diamond` | Name players see instead of the item name |
| `aliases` | `[old_name]` | Old names of a renamed custom spawner |
| `experience` | `1` | XP generated per spawner trigger |
| `nbt_data` | `nbt:...` | Exact item rendered as the rotating model inside the spawner cage |
| `item` (loot) | `DIAMOND` | The item that drops |
| `amount` | `1-1` | Base item quantity range per cycle |
| `chance` | `100.0` | Drop probability (0.0 to 100.0) |

`item` accepts a material name, a `/give` item string such as
`tipped_arrow[potion_contents={potion:"minecraft:poison"}]`, or an `nbt:` code copied out of the
game. See [Mob Spawners](/docs/spawner-mobs#naming-an-item) for the full explanation.

::: tip Material names
Every material value is a Bukkit material name in capital letters, for example `DIAMOND` or `NETHERITE_INGOT`. See the full list of valid names here: [Bukkit Material list](https://jd.papermc.io/paper/26.2/org/bukkit/Material.html).
:::

## Examples

### Basic Resource Spawner

```yaml
diamond:
  experience: 1
  loot:
    1:
      item: DIAMOND
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "DIAMOND"
    hash_texture: null
```

### Multiple Drop Types

```yaml
gold_ingot:
  experience: 1
  loot:
    1:
      item: GOLD_INGOT
      amount: 1-2
      chance: 100.0
    2:
      item: GOLD_NUGGET
      amount: 3-5
      chance: 50.0
```

### Custom Head Texture

```yaml
emerald:
  experience: 1
  loot:
    1:
      item: EMERALD
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "abc123def456..."
```

### Tipped Arrow Spawner

```yaml
tipped_arrow:
  experience: 1
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 8-16
      chance: 100.0
```

### Two Spawners for the Same Item

```yaml
diamond:
  experience: 1
  loot:
    1:
      item: DIAMOND
      amount: 1-1
      chance: 100.0

custom_spawners:
  rich_diamond:
    item: DIAMOND
    display_name: Rich Diamond
    loot:
      1:
        item: DIAMOND_BLOCK
        amount: 1-1
        chance: 10.0
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

- **View online:** [GitHub: spawner_items.yml](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawner_items.yml)
- **Reset:** Delete the file and restart to regenerate it.

::: info Upgrading from 1.8
A `spawner_items.yml` in the 1.8 layout is converted on the first start, the same way as [`spawner_mobs.yml`](/docs/spawner-mobs#default-configuration). The old file is kept as `spawner_items.yml.1.8-backup`.
:::

## Give Item Spawners

```bash
/ss give <player> <name> [amount]
```

Examples:
```bash
/ss give Steve diamond 1
/ss give Player123 rich_diamond 5
```
