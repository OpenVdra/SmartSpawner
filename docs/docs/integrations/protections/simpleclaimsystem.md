---
title: SimpleClaimSystem
---

# ![](https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp){.plugin-icon} SimpleClaimSystem

**Download:** [Modrinth](https://modrinth.com/plugin/simpleclaimsystem)

SmartSpawner supports both major SimpleClaimSystem generations. The permission names differ between them.

## SimpleClaimSystem 1.x

| Action | Permission |
|---|---|
| Open menu | `InteractBlocks` |
| Stack | `InteractBlocks` |
| Break | `Destroy` |

## SimpleClaimSystem 2.x

| Action | Permission |
|---|---|
| Open menu | `interact_spawner` |
| Stack | `interact_spawner` |
| Break | `destroy_block` **and** `destroy_spawners` |

In 2.x, breaking a spawner requires **both** `destroy_block` and `destroy_spawners` to be enabled for the player's role. Enabling only one is not enough.

Set these permissions per role in the SimpleClaimSystem claim settings. Outside any claim, all actions are allowed.
