# Item Spawners

The `spawner_items.yml` file in `plugins/SmartSpawner/` configures drop tables, XP values, and textures for **Item Spawners**, the spawner type that generates raw materials instead of mob drops.

## In-game Management

Use `/ss edit itemspawner` to edit existing entries. Its GUI is separate from the SmartSpawner mob
editor and has no switch button.

Run `/ss add itemspawner [name]` to create an entry. Put the source item into the middle slot of the
capture GUI and confirm. The name is optional, spaces become underscores, and the default is based on
the captured material (for example `diamond_spawner`). Existing names are not overwritten.

The captured item is stored losslessly as `nbt_data` and rendered inside the spawner cage. Item
components are retained, so a Jump Boost splash potion is shown as that potion instead of a plain
splash potion. Older entries without `nbt_data` use their first valid loot item as the preview.

The loot screen has 27 slots and no pagination or navigation items. One lime stained-glass pane follows
the last configured loot item; click it to add an item, and it moves one slot forward.

::: info Drop Multiplier
Each cycle generates drops between **min_mobs** and **max_mobs** times (default: 1–4). Configured amounts are base values that get multiplied.
:::

::: warning Limitations
Item spawners do not support potions or enchanted books. Only **tipped arrows** are supported with potion effects.
:::

## Configuration Format

```yaml
custom_spawner_name:
  item: ITEM_MATERIAL      # Required; controls the displayed/generated item type
  experience: <number>
  nbt_data: <captured item> # Written automatically by /ss add itemspawner
  loot:
    1:
      item: <item>          # Required
      amount: <min>-<max>
      chance: <percentage>
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>  # null for vanilla materials
```

## Properties Reference

| Property | Format | Description |
|----------|--------|-------------|
| `item` (spawner level) | `DIAMOND` | The item this named Item Spawner produces |
| `experience` | `1` | XP generated per spawner trigger |
| `nbt_data` | `nbt:...` | Exact item rendered as the rotating model inside the spawner cage |
| `item` | `DIAMOND` | The item that drops. Omit it to use the entry name. |
| `amount` | `1-1` | Base item quantity range per cycle |
| `chance` | `100.0` | Drop probability (0.0 to 100.0) |

Both `item` fields accept a material name, a `/give` item string such as
`tipped_arrow[potion_contents={potion:"minecraft:poison"}]`, an `nbt:` code copied out of the game,
or an item owned by another plugin such as `mmoitems:MATERIAL:RUBY`. See
[Spawner Settings](/docs/spawner-mobs) for the full explanation.

An Item Spawner is identified by its entry name, so several entries can produce items that share a
base material. Two entries built on `EMERALD`, one vanilla and one from
[MMOItems](/docs/integrations/mmoitems), stay separate spawners: they keep their own drop table, own
icon and own name, and they do not stack together.

::: tip Material names
Every `material` value is a Bukkit material name in capital letters, for example `DIAMOND` or `NETHERITE_INGOT`. See the full list of valid names here: [Bukkit Material list](https://jd.papermc.io/paper/26.2/org/bukkit/Material.html).
:::

## Examples

### Basic Resource Spawner

```yaml
diamond_spawner:
  item: DIAMOND
  experience: 1
  loot:
    1:
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "DIAMOND"
    hash_texture: null
```

### Multiple Drop Types

```yaml
gold_ingot_spawner:
  item: GOLD_INGOT
  experience: 1
  loot:
    1:
      amount: 1-2
      chance: 100.0
    2:
      amount: 3-5
      chance: 50.0
  mob_head:
    item: "GOLD_INGOT"
    hash_texture: null
```

### Custom Head Texture

```yaml
emerald_spawner:
  item: EMERALD
  experience: 1
  loot:
    1:
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "abc123def456..."
```

### Tipped Arrow Spawner

```yaml
tipped_arrow_spawner:
  item: TIPPED_ARROW
  experience: 1
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 8-16
      chance: 100.0
  mob_head:
    material: "TIPPED_ARROW"
    hash_texture: null
```

### Rare Item with Chance Drop

```yaml
totem_of_undying_spawner:
  item: TOTEM_OF_UNDYING
  experience: 2
  loot:
    1:
      amount: 1-1
      chance: 75.0
    2:
      amount: 1-3
      chance: 50.0
  mob_head:
    material: "TOTEM_OF_UNDYING"
    hash_texture: null
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

## Give Item Spawners

```bash
/ss give <player> item_spawner <name> [amount]
```

Examples:
```bash
/ss give Steve item_spawner diamond_spawner 1
/ss give Player123 item_spawner netherite_ingot_spawner 5
```
