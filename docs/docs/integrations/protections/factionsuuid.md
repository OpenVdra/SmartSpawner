---
title: FactionsUUID
---

# ![](/icons/integrations/factionsuuid.png){.plugin-icon} FactionsUUID

**Download:** [SpigotMC](https://www.spigotmc.org/resources/1035/)

SmartSpawner checks FactionsUUID territory access for all three actions.

| Action | Access permission |
|---|---|
| Open menu | `CONTAINER` |
| Stack | `BUILD` |
| Break | `DESTROY` |

## How access is decided

Inside a faction's claimed land, a player is allowed only when the faction grants them the matching access. Access is set per role and per relation (member, ally, truce, neutral, enemy) by the faction owner.

- Members of the faction that owns the land normally have full access.
- Other players follow the relation permissions the faction has configured.
- In wilderness, safezone, and warzone, this hook allows the action and leaves the decision to your other plugins.

Use `/f perm` to view and change the access settings for a faction.

::: warning Which FactionsUUID
This hook targets the modern FactionsUUID (`dev.kitteh`, version 4.x and newer), available from [factions.support](https://factions.support/). The older `com.massivecraft` builds use a different API and are not supported.
:::
