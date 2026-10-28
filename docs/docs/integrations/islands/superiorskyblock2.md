---
title: SuperiorSkyblock2
---

# ![](/icons/integrations/superiorskyblock2.png){.plugin-icon} SuperiorSkyblock2

**Download:** [SpigotMC](https://www.spigotmc.org/resources/87411/)

On startup SmartSpawner registers two island privileges.

| Action | Privilege |
|---|---|
| Open menu | `spawner_open_menu` |
| Stack | `spawner_stack` |
| Break | Not checked |

Breaking has no dedicated SmartSpawner privilege. SuperiorSkyblock2's own built-in block-break protection already guards the spawner block, so a player who cannot break blocks on the island cannot break the spawner either.

## Assign privileges

Give the privileges to the island roles that should be able to use spawners through the island permission menu:

```bash
/is permissions
```

## Cleanup

When an island is **disbanded**, SmartSpawner automatically cleans up the spawners it was tracking on that island. Outside any island, spawners are unrestricted.
