---
title: PlotSquared
---

# ![](/icons/integrations/plotsquared.png){.plugin-icon} PlotSquared

**Download:** [Modrinth](https://modrinth.com/plugin/plotsquared)

Access is decided by plot membership. A player may open, stack, or break a spawner only if they are **added or trusted** to the plot.

| Action | Requirement |
|---|---|
| Open menu | Added / trusted to plot |
| Stack | Added / trusted to plot |
| Break | Added / trusted to plot |

## Grant access

```bash
/plot trust <player>
/plot add <player>
```

## Cleanup

When a plot is deleted, SmartSpawner automatically removes the spawners it was tracking on that plot. Outside any plot, spawners are unrestricted.
