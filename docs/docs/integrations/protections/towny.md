---
title: Towny
---

# ![](https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp){.plugin-icon} Towny

**Download:** [Modrinth](https://modrinth.com/plugin/towny)

Towny has no per-spawner flag in this integration. Access is decided purely by town membership.

| Action | Requirement |
|---|---|
| Open menu | Resident or trusted resident of the town |
| Stack | Resident or trusted resident of the town |
| Break | Resident or trusted resident of the town |

## How it works

- A player may open, stack, or break a spawner only if they are a **resident** of the town that owns the plot, or a **trusted resident** of that town.
- Outside any town, spawners are unrestricted.
- The town's own `switch` and `destroy` permission lines are **not** consulted for this check. Membership is what matters.

## Grant access

Add the player to the town, or trust them:

```bash
/town add <player>
/town trust add <player>
```
