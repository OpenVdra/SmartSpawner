---
title: BlockLocker
---

# ![](https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1){.plugin-icon} BlockLocker

**Download:** [SpigotMC](https://www.spigotmc.org/resources/3268/)

BlockLocker protects single blocks with `[Private]` signs. SmartSpawner checks that protection for all three actions.

| Action | Who is allowed |
|---|---|
| Open menu | Owner or a trusted player |
| Stack | Owner or a trusted player |
| Break | Owner or a trusted player |

## How access is decided

When a spawner is locked with a `[Private]` sign, only the owner and the players listed on `[More Users]` signs may open its menu, stack it, or break it. Everyone else is blocked.

- A spawner that has no BlockLocker sign is treated as unprotected. SmartSpawner leaves that decision to your other plugins.
- Players with the BlockLocker bypass permission are allowed through, the same as they are for normal locked blocks.

## Making spawners lockable

BlockLocker only protects the block types listed in its own config. Spawners are not on that list out of the box, so you have to add them once.

1. Open `plugins/BlockLocker/config.yml`.
2. Find the `protectableContainers` list.
3. Add this line to it:

   ```yaml
   - minecraft:spawner
   ```

4. Restart the server so BlockLocker reads the change.

## Locking a spawner in game

1. Hold a sign and sneak.
2. While sneaking, right-click the spawner to place the sign on it.

The first line fills in as `[Private]` and your name becomes the owner. To let other players in, add a second sign that starts with `[More Users]` and list their names below it.

::: warning Sneak when placing the sign
Right-clicking a spawner without sneaking opens the SmartSpawner menu instead of placing the sign. Hold sneak so the sign goes on the spawner.
:::
