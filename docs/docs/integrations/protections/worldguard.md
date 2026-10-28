---
title: WorldGuard
---

# ![](https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp){.plugin-icon} WorldGuard

**Download:** [Modrinth](https://modrinth.com/plugin/worldguard)

SmartSpawner maps each spawner action to a WorldGuard region flag.

| Action | Flag |
|---|---|
| Open menu | `interact` |
| Stack | `block-place` |
| Break | `block-break` |

The check uses WorldGuard's build test, so **region members are always allowed**. For non-members (for example a public or global region) you must enable the flags explicitly.

## Allow spawner use in a region

```bash
/rg flag <region> interact allow
/rg flag <region> block-place allow
/rg flag <region> block-break allow
```

## Block spawner use for everyone

Setting a flag to `deny` blocks it even for members:

```bash
/rg flag <region> interact deny
```

To apply the rule to a whole world, set the flags on the `__global__` region.

## Bypass

Operators and players with `worldguard.region.bypass` ignore these flags.
