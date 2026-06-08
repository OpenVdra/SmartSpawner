---
title: API Events
description: Event handlers for spawner-related actions and interactions.
---
## API Events

SmartSpawner provides various events to hook into spawner-related actions:

| Event                     | Description                                      | Cancellable |
|---------------------------|--------------------------------------------------|:----------:|
| `SpawnerBreakEvent`       | Spawner broken by a player or an explosion       |      ❌     |
| `SpawnerPlaceEvent`       | Spawner placed by player                         |      ✅     |
| `SpawnerPlayerBreakEvent` | Spawner broken by player                         |      ✅     |
| `SpawnerStackEvent`       | Spawners stacking by hand                        |      ✅     |
| `SpawnerSellEvent`        | Selling item from spawner storage                |      ✅     |
| `SpawnerExpClaimEvent`    | Experience claimed from spawner                  |      ✅     |
| `SpawnerEggChangeEvent`   | Spawner type changed with egg                    |      ✅     |
| `SpawnerExplodeEvent`     | Spawners destroyed by explosion                  |      ❌     |
| `SpawnerRemoveEvent`      | Unstack spawners from the stacker GUI            |      ✅     |
| `SpawnerDestroyEvent`     | Spawner removed programmatically through the API   |      ✅     |
| `SpawnerOpenGUIEvent`     | GUI opened by player                             |      ✅     |
| `SpawnerDropAllEvent`     | Dropping all item from a page of spawner storage |      ✅      |
| `SpawnerTakeAllEvent`     | Taking all item from a page of spawner storage   |      ✅      |

### SpawnerBreakEvent
Triggered when a spawner is broken by a player or explosion.

```java
import github.nighter.smartspawner.api.events.SpawnerBreakEvent;

@EventHandler
public void onSpawnerBreak(SpawnerBreakEvent event) {
    Entity breaker = event.getEntity();
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    // Handle spawner break
    if (breaker instanceof Player) {
        Player player = (Player) breaker;
        player.sendMessage("You broke " + quantity + " spawner(s)!");
    }
}
```

### SpawnerPlaceEvent
Triggered when a spawner is placed.

```java
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;

@EventHandler
public void onSpawnerPlace(SpawnerPlaceEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    
    // Handle spawner placement
    player.sendMessage("Spawner placed at " + location.toString());
}
```

### SpawnerPlayerBreakEvent
Triggered specifically when a player breaks a spawner.

```java
import github.nighter.smartspawner.api.events.SpawnerPlayerBreakEvent;

@EventHandler
public void onPlayerBreakSpawner(SpawnerPlayerBreakEvent event) {
    Player player = event.getPlayer();
    int quantity = event.getQuantity();
    
    // Cancel if player doesn't have permission
    if (!player.hasPermission("spawner.break")) {
        event.setCancelled(true);
        player.sendMessage("No permission to break spawners!");
    }
}
```

### SpawnerStackEvent
Triggered when spawners are stacked by hand.

```java
import github.nighter.smartspawner.api.events.SpawnerStackEvent;

@EventHandler
public void onSpawnerStack(SpawnerStackEvent event) {
    Player player = event.getPlayer();
    int newStackSize = event.getNewStackSize();
    
    player.sendMessage("Spawner stacked! New size: " + newStackSize);
}
```

### SpawnerSellEvent
Triggered when items are sold from spawner storage.

```java
import github.nighter.smartspawner.api.events.SpawnerSellEvent;

@EventHandler
public void onSpawnerSell(SpawnerSellEvent event) {
    Player player = event.getPlayer();
    double price = event.getPrice();
    
    // Add bonus money
    double bonus = price * 0.1; // 10% bonus
    // Give bonus to player via your economy plugin
}
```

### SpawnerExpClaimEvent
Triggered when experience is claimed from spawners.

```java
import github.nighter.smartspawner.api.events.SpawnerExpClaimEvent;

@EventHandler
public void onExpClaim(SpawnerExpClaimEvent event) {
    Player player = event.getPlayer();
    int expAmount = event.getExpAmount();
    
    // Modify experience amount
    event.setExpAmount(expAmount * 2); // Double EXP
}
```

### SpawnerEggChangeEvent
Triggered when a spawner's entity type is changed using spawn eggs.

```java
import github.nighter.smartspawner.api.events.SpawnerEggChangeEvent;

@EventHandler
public void onSpawnerEggChange(SpawnerEggChangeEvent event) {
    Player player = event.getPlayer();
    EntityType oldType = event.getOldEntityType();
    EntityType newType = event.getNewEntityType();
    
    player.sendMessage("Changed spawner from " + oldType + " to " + newType);
}
```

### SpawnerExplodeEvent
Triggered when spawners are destroyed by explosions.

```java
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;

@EventHandler
public void onSpawnerExplode(SpawnerExplodeEvent event) {
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    // Log explosion
    getLogger().info("Spawners destroyed by explosion at " + location);
}
```

### SpawnerRemoveEvent
Triggered when spawners are unstacked from the stacker GUI.

```java
import github.nighter.smartspawner.api.events.SpawnerRemoveEvent;

@EventHandler
public void onSpawnerRemove(SpawnerRemoveEvent event) {
    Location location = event.getLocation();
    
    // Handle spawner removal
    getLogger().info("Spawner removed at " + location);
}
```

### SpawnerOpenGUIEvent
Triggered when a player opens the spawner GUI.

```java
import github.nighter.smartspawner.api.events.SpawnerOpenGUIEvent;

@EventHandler
public void onSpawnerOpenGUI(SpawnerOpenGUIEvent event) {
    Player player = event.getPlayer();
    EntityType entityType = event.getEntityType();
    boolean isRefresh = event.isRefresh();
    
    // Handle GUI open
    if (!player.hasPermission("spawner.gui.open")) {
        event.setCancelled(true);
        player.sendMessage("No permission to open spawner GUI!");
    }
}
```

### SpawnerDropAllEvent
Triggered when a player drop all items from a page of spawner storage.

```java
import github.nighter.smartspawner.api.events.SpawnerDropAllEvent;

@EventHandler
public void onSpawnerDropAll(SpawnerDropAllEvent event) {
    Player player = event.getPlayer();
    
    // Handle drop all
    if (!player.hasPermission("spawner.gui.dropall")) {
        event.setCancelled(true);
        player.sendMessage("No permission to drop all items from spawner!");
    }
}
```

### SpawnerTakeAllEvent
Triggered when a player take all items from a page of spawner storage.

```java
import github.nighter.smartspawner.api.events.SpawnerTakeAllEvent;

@EventHandler
public void onSpawnerTakeAll(SpawnerTakeAllEvent event) {
    Player player = event.getPlayer();
    
    // Handle take all
    if (!player.hasPermission("spawner.gui.takeall")) {
        event.setCancelled(true);
        player.sendMessage("No permission to take all items from spawner!");
    }
}
```

<br>
<br>

---

*Last update: March 19, 2026 17:08*