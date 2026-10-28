---
title: GriefPrevention
---

# ![](https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp){.plugin-icon} GriefPrevention

**Download:** [Modrinth](https://modrinth.com/plugin/griefprevention)

SmartSpawner uses a different trust level for each spawner action, the same way GriefPrevention treats other blocks. Opening a spawner's storage menu needs **container trust**. Stacking and breaking a spawner need **build trust**.

| Action | Requirement |
|---|---|
| Open menu | Container trust |
| Stack | Build trust |
| Break | Build trust |

Build trust also covers opening the menu, so a build-trusted player can do everything.

## Grant access

```bash
/containertrust <player>
```

`/containertrust` lets a player open and use a spawner's storage menu.

```bash
/trust <player>
```

`/trust` gives full build trust, which covers opening, stacking, and breaking spawners.

## Commands that are not enough

- `/accesstrust` allows buttons and doors only, not the storage menu.

Outside any claim, all actions are allowed.
