---
title: Spawner Data Access
description: Methods for accessing and modifying spawner data and properties.
---
## Spawner Data Access Methods

| Method | Description | Return Type |
|--------|-------------|-------------|
| `getSpawnerByLocation(Location)` | Gets spawner data by block location | `SpawnerDataDTO` |
| `getSpawnerById(String)` | Gets spawner data by unique ID | `SpawnerDataDTO` |
| `getAllSpawners()` | Gets all registered spawners | `List<SpawnerDataDTO>` |
| `getSpawnerModifier(String)` | Gets modifier to change spawner properties | `SpawnerDataModifier` |
| `removeSpawner(String)` | Removes by unique ID and completes when block/data cleanup finishes | `CompletableFuture<Boolean>` |
| `removeSpawner(Location)` | Removes by location and completes when block/data cleanup finishes | `CompletableFuture<Boolean>` |

### SpawnerDataDTO

The `SpawnerDataDTO` class provides **read-only** access to spawner information. To modify spawner properties, use `SpawnerDataModifier`:

| Method | Description | Return Type |
|--------|-------------|-------------|
| `getSpawnerId()` | Gets the unique spawner ID | `String` |
| `getLocation()` | Gets the spawner location | `Location` |
| `getEntityType()` | Gets the entity type | `EntityType` |
| `getSpawnedItemMaterial()` | Gets spawned item material (for item spawners) | `Material` |
| `getStackSize()` | Gets current stack size | `int` |
| `getMaxStackSize()` | Gets maximum stack size | `int` |
| `getBaseMaxStoragePages()` | Gets base storage pages | `int` |
| `getBaseMinMobs()` | Gets base minimum mobs | `int` |
| `getBaseMaxMobs()` | Gets base maximum mobs | `int` |
| `getBaseMaxStoredExp()` | Gets base maximum stored experience | `int` |
| `getBaseSpawnerDelay()` | Gets base spawner delay in ticks | `long` |
| `isItemSpawner()` | Checks if this is an item spawner | `boolean` |

### SpawnerDataModifier

The `SpawnerDataModifier` interface is the **only way to modify spawner properties**. It provides method chaining and requires calling `applyChanges()` to save modifications. Note: `stackSize` is read-only and cannot be modified:

| Method | Description | Return Type |
|--------|-------------|-------------|
| `getStackSize()` | Gets current stack size (read-only) | `int` |
| `getMaxStackSize()` | Gets maximum stack size | `int` |
| `setMaxStackSize(int)` | Sets maximum stack size (chainable) | `SpawnerDataModifier` |
| `getBaseMaxStoragePages()` | Gets base storage pages | `int` |
| `setBaseMaxStoragePages(int)` | Sets base storage pages (chainable) | `SpawnerDataModifier` |
| `getBaseMinMobs()` | Gets base minimum mobs | `int` |
| `setBaseMinMobs(int)` | Sets base minimum mobs (chainable) | `SpawnerDataModifier` |
| `getBaseMaxMobs()` | Gets base maximum mobs | `int` |
| `setBaseMaxMobs(int)` | Sets base maximum mobs (chainable) | `SpawnerDataModifier` |
| `getBaseMaxStoredExp()` | Gets base maximum stored experience | `int` |
| `setBaseMaxStoredExp(int)` | Sets base maximum stored experience (chainable) | `SpawnerDataModifier` |
| `getBaseSpawnerDelay()` | Gets base spawner delay in ticks | `long` |
| `setBaseSpawnerDelay(long)` | Sets base spawner delay in ticks (chainable) | `SpawnerDataModifier` |
| `applyChanges()` | Applies and recalculates all changes | `void` |

## Usage Examples

### `getSpawnerByLocation()`

Gets spawner data by its block location.

```java
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import org.bukkit.Location;

Location location = block.getLocation();
SpawnerDataDTO spawnerData = api.getSpawnerByLocation(location);

if (spawnerData != null) {
    // READ-ONLY: Can only read data, cannot modify
    player.sendMessage("Spawner ID: " + spawnerData.getSpawnerId());
    player.sendMessage("Entity Type: " + spawnerData.getEntityType());
    player.sendMessage("Stack Size: " + spawnerData.getStackSize());
    player.sendMessage("Base Delay: " + spawnerData.getBaseSpawnerDelay() + " ticks");
    
    // To modify values, use SpawnerDataModifier
    SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerData.getSpawnerId());
    if (modifier != null) {
        modifier.setBaseMaxMobs(10)
                .setBaseMinMobs(2)
                .setBaseSpawnerDelay(600L)
                .applyChanges(); // Must call to save changes
        player.sendMessage("Values updated!");
    }
}
```

### `getSpawnerById()`

Gets spawner data by its unique identifier.

```java
String spawnerId = "spawner-uuid-here";
SpawnerDataDTO spawnerData = api.getSpawnerById(spawnerId);

if (spawnerData != null) {
    // READ-ONLY: Can only read data
    Location location = spawnerData.getLocation();
    player.sendMessage("Spawner location: " + location);
    player.sendMessage("Max Stack: " + spawnerData.getMaxStackSize());
    
    // To modify, use SpawnerDataModifier
    SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerId);
    if (modifier != null) {
        modifier.setMaxStackSize(2000)
                .applyChanges();
        player.sendMessage("Max stack size updated!");
    }
}
```

### `getAllSpawners()`

Gets all registered spawners in the server.

```java
import java.util.List;

List<SpawnerDataDTO> allSpawners = api.getAllSpawners();
player.sendMessage("Total spawners: " + allSpawners.size());

for (SpawnerDataDTO spawner : allSpawners) {
    player.sendMessage("- " + spawner.getEntityType() + 
                      " at " + spawner.getLocation() + 
                      " (Stack: " + spawner.getStackSize() + ")");
}
```

### `getSpawnerModifier()`

Modifies spawner properties through the API with method chaining.

```java
import github.nighter.smartspawner.api.data.SpawnerDataModifier;

// Get the spawner modifier
String spawnerId = "spawner-uuid-here";
SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerId);

if (modifier != null) {
    // Read current values
    int currentStack = modifier.getStackSize(); // Read-only
    long currentDelay = modifier.getBaseSpawnerDelay();
    
    // Modify multiple values with method chaining
    modifier.setBaseMaxMobs(15)
            .setBaseMinMobs(5)
            .setBaseSpawnerDelay(400L)
            .setBaseMaxStoredExp(5000)
            .setBaseMaxStoragePages(3)
            .applyChanges(); // Must call to apply changes and recalculate
    
    player.sendMessage("Spawner configuration updated!");
    player.sendMessage("Note: Stack size cannot be modified (read-only)");
}
```

### `removeSpawner()`

Removes a spawner completely from the server, including its block, data, hopper tracking, and open inventories. The method returns a `CompletableFuture<Boolean>` because the spawner chunk may need to be loaded asynchronously before SmartSpawner can safely set the block to air.

The future completes with `true` after block and data cleanup finishes. It completes with `false` if the spawner does not exist, is already being removed, or is being modified by another protected operation.

```java
import org.bukkit.Location;

// Option 1: Remove by Spawner ID
String spawnerId = "spawner-uuid-here";
api.removeSpawner(spawnerId).thenAccept(removed -> {
    if (removed) {
        player.sendMessage("Spawner fully removed by ID!");
    } else {
        player.sendMessage("Spawner not found, already being removed, or currently being modified!");
    }
});

// Option 2: Remove by Location
Location spawnerLocation = player.getTargetBlock(null, 5).getLocation();
api.removeSpawner(spawnerLocation).thenAccept(removed -> {
    if (removed) {
        player.sendMessage("Spawner fully removed by location!");
    } else {
        player.sendMessage("No spawner found at that location, or it is already being modified!");
    }
});
```

### Reading and Modifying Base Values

All base values can be both read and modified:

```java
SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerId);

if (modifier != null) {
    // Read current values
    int maxStoragePages = modifier.getBaseMaxStoragePages();
    int minMobs = modifier.getBaseMinMobs();
    int maxMobs = modifier.getBaseMaxMobs();
    int maxStoredExp = modifier.getBaseMaxStoredExp();
    long spawnerDelay = modifier.getBaseSpawnerDelay();
    
    player.sendMessage("Current Config Values:");
    player.sendMessage("Storage Pages: " + maxStoragePages);
    player.sendMessage("Min Mobs: " + minMobs);
    player.sendMessage("Max Mobs: " + maxMobs);
    player.sendMessage("Max Exp: " + maxStoredExp);
    player.sendMessage("Delay: " + spawnerDelay + " ticks");
    
    // Modify values
    modifier.setBaseMaxStoragePages(5)
            .setBaseMinMobs(3)
            .setBaseMaxMobs(20)
            .setBaseMaxStoredExp(10000)
            .setBaseSpawnerDelay(300L)
            .applyChanges();
    
    player.sendMessage("All values updated successfully!");
}
```

<br>
<br>

---

*Last update: November 17, 2025 11:46:34*
