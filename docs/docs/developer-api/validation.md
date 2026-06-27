# Validation Methods

Methods for identifying and inspecting spawner items.

## Method Reference

| Method | Description | Returns |
|--------|-------------|---------|
| `isSmartSpawner(ItemStack)` | Check if item is a Smart Spawner | `boolean` |
| `isVanillaSpawner(ItemStack)` | Check if item is a vanilla spawner | `boolean` |
| `isItemSpawner(ItemStack)` | Check if item is an item spawner | `boolean` |
| `getSpawnerEntityType(ItemStack)` | Get the entity type from any spawner | `EntityType` |
| `getItemSpawnerMaterial(ItemStack)` | Get the material from an item spawner | `Material` |

## Examples

### Identify Spawner Type

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    ItemStack item = event.getItem();
    if (item == null) return;

    if (api.isSmartSpawner(item)) {
        player.sendMessage("Smart Spawner: " + api.getSpawnerEntityType(item));
    } else if (api.isVanillaSpawner(item)) {
        player.sendMessage("Vanilla Spawner: " + api.getSpawnerEntityType(item));
    } else if (api.isItemSpawner(item)) {
        player.sendMessage("Item Spawner: " + api.getItemSpawnerMaterial(item));
    }
}
```

### Get Entity Type

```java
ItemStack item = player.getItemInHand();
EntityType type = api.getSpawnerEntityType(item);

if (type != null) {
    player.sendMessage("Spawns: " + type.name());
} else {
    player.sendMessage("Not a valid spawner.");
}
```

### Get Item Spawner Material

```java
ItemStack item = player.getItemInHand();

if (api.isItemSpawner(item)) {
    Material mat = api.getItemSpawnerMaterial(item);
    if (mat != null) {
        player.sendMessage("Generates: " + mat.name());
    }
}
```
