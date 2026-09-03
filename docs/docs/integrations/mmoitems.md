---
title: MMOItems
---

# MMOItems

**Download:** [Spigot](https://www.spigotmc.org/resources/mmoitems.39267/)

An MMOItems item can be named anywhere SmartSpawner reads an `item` value: as a drop in a loot table,
and as the item an Item Spawner produces.

```text
mmoitems:<TYPE>:<ID>
```

`TYPE` is the MMOItems item type, `ID` is the item id inside it. Both are matched in capital letters.

## A spawner that drops an MMOItems item

```yaml
# spawner_mobs.yml
zombie_spawner:
  entity: ZOMBIE
  loot:
    1:
      item: mmoitems:MATERIAL:RUBY
      amount: 1-1
      chance: 5.0
```

## A spawner that is an MMOItems item

```yaml
# spawner_items.yml
ruby_spawner:
  item: mmoitems:MATERIAL:RUBY
  experience: 1
  loot:
    1:
      item: mmoitems:MATERIAL:RUBY
      amount: 1-1
      chance: 100.0
```

Give it out with `/ss give <player> item_spawner ruby_spawner`. The spawner takes its name, its menu
icon and the item rotating inside the cage from the MMOItems item itself.

Several entries may produce items that share a base material. They stay separate spawners with their
own drop table, name and icon, and they do not stack together.

## Selling

A shop plugin can only price a vanilla material, so an MMOItems item would otherwise be sold for what
its base material is worth. Give it a price of its own in `sell_integration.yml`, under the exact
value the loot entry names:

```yaml
custom_prices:
  prices:
    "mmoitems:MATERIAL:RUBY": 250.0
```

That price is used whatever `price_source_mode` is set to. Without an entry the item falls back to
its base material's price.

## Notes

- The item is built once, when the configuration is read. An MMOItems template that rolls random
  stats is rolled once, so every copy the spawner produces is identical and stacks normally.
- After `/mmoitems reload`, run `/ss reload` so SmartSpawner picks up the rebuilt templates.
- With MMOItems absent, an entry naming one is skipped and reported in the console, and an Item
  Spawner already placed falls back to its base material instead of disappearing.
- A wrong type or id is reported separately in the console, so a typo is easy to tell from a missing
  plugin.
