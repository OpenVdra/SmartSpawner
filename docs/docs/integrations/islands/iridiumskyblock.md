---
title: IridiumSkyblock
---

# ![](https://cdn.modrinth.com/data/uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png){.plugin-icon} IridiumSkyblock

**Download:** [Modrinth](https://modrinth.com/plugin/iridiumskyblock)

SmartSpawner adds two permissions to the IridiumSkyblock island permission system.

| Action | Permission |
|---|---|
| Open menu | `SpawnerOpenMenuPermission` |
| Stack | `SpawnerStackPermission` |
| Break | Not checked |

Breaking has no dedicated SmartSpawner permission. IridiumSkyblock's own built-in block-break protection already guards the spawner block, so a player who cannot break blocks on the island cannot break the spawner either.

## Assign permissions

Toggle the permissions per island rank in the IridiumSkyblock permissions menu.

## Labels

The permission name and lore shown in the IridiumSkyblock menu are stored per locale in:

```text
plugins/SmartSpawner/language/<locale>/iridium_skyblock.yml
```
