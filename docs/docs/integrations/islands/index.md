---
title: Islands and Plots
---

# Islands and Plots

Like [protections](/docs/integrations/protections/), island and plot hooks are automatic. SmartSpawner asks the plugin before a player opens the menu, stacks, or breaks a spawner.

::: tip Who bypasses every check
Operators and players with the `*` wildcard permission skip all checks. Test with a regular player account.
:::

## Quick reference

| Plugin | Open menu | Stack | Break |
|---|---|---|---|
| ![](/icons/integrations/plotsquared.png){.plugin-icon} [PlotSquared](/docs/integrations/islands/plotsquared) | Added / trusted to plot | Added / trusted to plot | Added / trusted to plot |
| ![](/icons/integrations/mineplots.svg){.plugin-icon} [minePlots](/docs/integrations/islands/mineplots) | Plot access | Plot access | Plot access |
| ![](/icons/integrations/superiorskyblock2.png){.plugin-icon} [SuperiorSkyblock2](/docs/integrations/islands/superiorskyblock2) | `spawner_open_menu` | `spawner_stack` | Not checked |
| ![](https://cdn.modrinth.com/data/aBVLHiAW/2ad201b237497093c4957095fa9f97bf61708ed1_96.webp){.plugin-icon} [BentoBox](/docs/integrations/islands/bentobox) | `CONTAINER` | `PLACE_BLOCKS` | Not checked |
| ![](https://cdn.modrinth.com/data/uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png){.plugin-icon} [IridiumSkyblock](/docs/integrations/islands/iridiumskyblock) | `SpawnerOpenMenuPermission` | `SpawnerStackPermission` | Not checked |

Outside any plot or island, every action is allowed by default.
