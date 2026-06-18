# API Events

SmartSpawner fires Bukkit events you can listen to in your plugin.

## Event Reference

| Event | Description | Cancellable |
|-------|-------------|:-----------:|
| `SpawnerBreakEvent` | Spawner broken by player or explosion | No |
| `SpawnerPlaceEvent` | Spawner placed by player | Yes |
| `SpawnerPlayerBreakEvent` | Spawner broken specifically by a player | Yes |
| `SpawnerStackEvent` | Spawners stacked by hand | Yes |
| `SpawnerSellEvent` | Items sold from spawner storage | Yes |
| `SpawnerExpClaimEvent` | Experience claimed from spawner | Yes |
| `SpawnerEggChangeEvent` | Spawner type changed with spawn egg | Yes |
| `SpawnerExplodeEvent` | Spawner destroyed by explosion | No |
| `SpawnerRemoveEvent` | Spawners unstacked via GUI | Yes |
| `SpawnerOpenGUIEvent` | Spawner GUI opened by player | Yes |
| `SpawnerDropAllEvent` | All items on a storage page dropped | Yes |
| `SpawnerTakeAllEvent` | All items on a storage page taken | Yes |

## Event Examples

### SpawnerBreakEvent

```java
@EventHandler
public void onSpawnerBreak(SpawnerBreakEvent event) {
    Entity breaker = event.getEntity();
    Location location = event.getLocation();
    int quantity = event.getQuantity();

    if (breaker instanceof Player player) {
        player.sendMessage("You broke " + quantity + " spawner(s)!");
    }
}
```

### SpawnerPlaceEvent

```java
@EventHandler
public void onSpawnerPlace(SpawnerPlaceEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();

    player.sendMessage("Spawner placed at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
}
```

### SpawnerPlayerBreakEvent

```java
@EventHandler
public void onPlayerBreakSpawner(SpawnerPlayerBreakEvent event) {
    Player player = event.getPlayer();

    if (!player.hasPermission("yourplugin.break")) {
        event.setCancelled(true);
        player.sendMessage("You cannot break spawners here.");
    }
}
```

### SpawnerStackEvent

```java
@EventHandler
public void onSpawnerStack(SpawnerStackEvent event) {
    Player player = event.getPlayer();
    int newStackSize = event.getNewStackSize();

    player.sendMessage("New stack size: " + newStackSize);
}
```

### SpawnerSellEvent

```java
@EventHandler
public void onSpawnerSell(SpawnerSellEvent event) {
    Player player = event.getPlayer();
    double price = event.getPrice();

    // Give a 10% bonus on top
    double bonus = price * 0.1;
    // economy.depositPlayer(player, bonus);
}
```

### SpawnerExpClaimEvent

```java
@EventHandler
public void onExpClaim(SpawnerExpClaimEvent event) {
    Player player = event.getPlayer();
    int expAmount = event.getExpAmount();

    // Double the XP
    event.setExpAmount(expAmount * 2);
}
```

### SpawnerEggChangeEvent

```java
@EventHandler
public void onSpawnerEggChange(SpawnerEggChangeEvent event) {
    Player player = event.getPlayer();
    EntityType oldType = event.getOldEntityType();
    EntityType newType = event.getNewEntityType();

    player.sendMessage("Changed from " + oldType + " to " + newType);
}
```

### SpawnerOpenGUIEvent

```java
@EventHandler
public void onSpawnerOpenGUI(SpawnerOpenGUIEvent event) {
    Player player = event.getPlayer();

    if (!player.hasPermission("yourplugin.gui")) {
        event.setCancelled(true);
        player.sendMessage("You cannot open spawner GUIs.");
    }
}
```

### SpawnerDropAllEvent / SpawnerTakeAllEvent

```java
@EventHandler
public void onSpawnerDropAll(SpawnerDropAllEvent event) {
    Player player = event.getPlayer();

    if (!player.hasPermission("yourplugin.dropall")) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onSpawnerTakeAll(SpawnerTakeAllEvent event) {
    Player player = event.getPlayer();

    if (!player.hasPermission("yourplugin.takeall")) {
        event.setCancelled(true);
    }
}
```
