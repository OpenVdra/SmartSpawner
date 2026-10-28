---
title: Residence
---

# ![](/icons/integrations/residence.jpg){.plugin-icon} Residence

**Download:** [SpigotMC](https://www.spigotmc.org/resources/11480/)

SmartSpawner maps spawner actions to Residence flags. Note that opening the menu uses the `use` flag, **not** `container`.

| Action | Flag |
|---|---|
| Open menu | `use` |
| Stack | `build` |
| Break | `build` |

## Configure flags

```bash
# Whole residence
/res set <residence> use true
/res set <residence> build true

# Per player
/res pset <residence> <player> use,build true
```

Outside any residence, all actions are allowed. If the flag is not set, Residence's global default for that flag applies.
