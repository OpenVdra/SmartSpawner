---
title: Protections and Claims
---

# Protections and Claims

Protection hooks are **automatic**. There is no toggle in `config.yml`. When a supported protection plugin is present, SmartSpawner asks it before three actions:

- **Open menu**: right-click a spawner to open the GUI.
- **Stack**: add spawners to a stack.
- **Break**: mine or remove a spawner.

The flag or trust level that allows each action lives inside the protection plugin, not inside SmartSpawner. Pick your plugin below for the exact settings.

::: tip Who bypasses every check
Operators and players with the `*` wildcard permission (plus `worldguard.region.bypass` for WorldGuard) skip all protection checks. Test with a regular player account.
:::

## Quick reference

| Plugin | Open menu | Stack | Break |
|---|---|---|---|
| ![](https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp){.plugin-icon} [WorldGuard](/docs/integrations/protections/worldguard) | `interact` | `block-place` | `block-break` |
| ![](https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp){.plugin-icon} [GriefPrevention](/docs/integrations/protections/griefprevention) | Container trust | Build trust | Build trust |
| ![](/icons/integrations/lands.jpg){.plugin-icon} [Lands](/docs/integrations/protections/lands) | `INTERACT_CONTAINER` | `BLOCK_PLACE` | `BLOCK_BREAK` |
| ![](https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp){.plugin-icon} [Towny](/docs/integrations/protections/towny) | Resident / trusted | Resident / trusted | Resident / trusted |
| ![](/icons/integrations/residence.jpg){.plugin-icon} [Residence](/docs/integrations/protections/residence) | `use` | `build` | `build` |
| ![](https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp){.plugin-icon} [RedProtect](/docs/integrations/protections/redprotect) | `chest` | `build` | Not checked |
| ![](https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp){.plugin-icon} [SimpleClaimSystem](/docs/integrations/protections/simpleclaimsystem) | `InteractBlocks` / `interact_spawner` | Same | `Destroy` / spawner perms |
| ![](/icons/integrations/factionsuuid.png){.plugin-icon} [FactionsUUID](/docs/integrations/protections/factionsuuid) | `CONTAINER` | `BUILD` | `DESTROY` |
| ![](https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1){.plugin-icon} [BlockLocker](/docs/integrations/protections/blocklocker) | Owner / trusted | Owner / trusted | Owner / trusted |

Outside any region or claim, every action is allowed by default.

For plot and island plugins (PlotSquared, SuperiorSkyblock2, BentoBox, and more), see [Islands and Plots](/docs/integrations/islands/).
