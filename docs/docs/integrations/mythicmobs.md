---
title: MythicMobs
---

# ![](https://cdn.modrinth.com/data/NLuc7Jjh/80ddd8e4535a8305eac4edfd5ced1e8e5344e11d_96.webp){.plugin-icon .plugin-icon-dark} MythicMobs

**Download:** [Modrinth](https://modrinth.com/plugin/mythicmobs)

SmartSpawner registers a MythicMobs drop named `smartspawner`. Use it in a MythicMobs drop table to create a Smart Spawner item:

```yaml
ExampleDrops:
  Drops:
    - smartspawner ZOMBIE 1
    - smartspawner SKELETON 1-3
```

Syntax:

```text
smartspawner <ENTITY_TYPE> [amount|minimum-maximum]
```

The mob must map to a valid Bukkit `EntityType`. Invalid mob names or ranges are rejected and logged.
