---
title: RedProtect
---

# ![](https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp){.plugin-icon} RedProtect

**Download:** [SpigotMC](https://www.spigotmc.org/resources/15841/)

SmartSpawner checks RedProtect region permissions for two of the three actions.

| Action | Permission |
|---|---|
| Open menu | `chest` (canChest) |
| Stack | `build` (canBuild) |
| Break | Not checked by this hook |

## Notes

- Opening the menu needs the region's **chest** permission.
- Stacking needs the region's **build** permission.
- Breaking is not gated by the RedProtect hook. RedProtect's own block-break protection still applies through the normal Bukkit events, so a player who cannot break blocks in the region still cannot break the spawner.

Default flags can be edited in `plugins/RedProtect/<world>/config.yml` and the flag files. Use `/rp flag info` to inspect a region.
