# API Creation Methods

Methods for creating SmartSpawner, vanilla spawner, and item spawner items programmatically.

## Method Reference

| Method | Description | Returns |
|--------|-------------|---------|
| `createSpawnerItem(EntityType)` | Create a Smart Spawner item | `ItemStack` |
| `createSpawnerItem(EntityType, int)` | Create multiple Smart Spawner items | `ItemStack` |
| `createVanillaSpawnerItem(EntityType)` | Create a vanilla spawner item | `ItemStack` |
| `createVanillaSpawnerItem(EntityType, int)` | Create multiple vanilla spawner items | `ItemStack` |
| `createItemSpawnerItem(Material)` | Create an item spawner | `ItemStack` |
| `createItemSpawnerItem(Material, int)` | Create multiple item spawners | `ItemStack` |

## Smart Spawners

```java
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

// Single zombie spawner
ItemStack zombieSpawner = api.createSpawnerItem(EntityType.ZOMBIE);

// Multiple skeleton spawners (stacked)
ItemStack skeletonSpawners = api.createSpawnerItem(EntityType.SKELETON, 5);

// Give to player
player.getInventory().addItem(zombieSpawner);
```

## Vanilla Spawners

```java
// Single vanilla creeper spawner
ItemStack vanillaSpawner = api.createVanillaSpawnerItem(EntityType.CREEPER);

// Multiple vanilla spawners
ItemStack vanillaSpawners = api.createVanillaSpawnerItem(EntityType.COW, 3);
```

## Item Spawners

```java
import org.bukkit.Material;

// Diamond item spawner
ItemStack diamondSpawner = api.createItemSpawnerItem(Material.DIAMOND);

// Multiple gold ingot spawners
ItemStack goldSpawners = api.createItemSpawnerItem(Material.GOLD_INGOT, 10);
```
