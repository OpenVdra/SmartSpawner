# API Examples

Complete working examples demonstrating common SmartSpawner API patterns.

## Example 1: Inspect a Spawner Item

Check what type of spawner a player is holding.

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerChecker implements Listener {

    private final SmartSpawnerAPI api;

    public SpawnerChecker(SmartSpawnerAPI api) {
        this.api = api;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.SPAWNER) return;

        if (api.isSmartSpawner(item)) {
            EntityType type = api.getSpawnerEntityType(item);
            player.sendMessage("§aSmartSpawner: §e" + type);
        } else if (api.isVanillaSpawner(item)) {
            EntityType type = api.getSpawnerEntityType(item);
            player.sendMessage("§7Vanilla Spawner: §e" + type);
        } else if (api.isItemSpawner(item)) {
            Material mat = api.getItemSpawnerMaterial(item);
            player.sendMessage("§6Item Spawner: §e" + mat);
        }
    }
}
```

## Example 2: Spawner Upgrade Command

Read and upgrade the spawner the player is looking at.

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnerCommand implements CommandExecutor {

    private final SmartSpawnerAPI api;

    public SpawnerCommand(SmartSpawnerAPI api) {
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only!");
            return true;
        }

        SpawnerDataDTO spawner = api.getSpawnerByLocation(
            player.getTargetBlock(null, 5).getLocation()
        );

        if (spawner == null) {
            player.sendMessage("§cNo spawner found.");
            return true;
        }

        player.sendMessage("§6ID: §f" + spawner.getSpawnerId());
        player.sendMessage("§6Entity: §f" + spawner.getEntityType());
        player.sendMessage("§6Stack: §f" + spawner.getStackSize() + " (read-only)");

        if (args.length > 0 && args[0].equalsIgnoreCase("upgrade")) {
            SpawnerDataModifier mod = api.getSpawnerModifier(spawner.getSpawnerId());
            if (mod != null) {
                mod.setBaseMaxMobs(mod.getBaseMaxMobs() + 2)
                   .setBaseMinMobs(mod.getBaseMinMobs() + 1)
                   .setBaseMaxStoredExp(mod.getBaseMaxStoredExp() + 500)
                   .setBaseMaxStoragePages(mod.getBaseMaxStoragePages() + 1)
                   .applyChanges();
                player.sendMessage("§aSpawner upgraded!");
            }
        }

        return true;
    }
}
```

## Example 3: Server-Wide Statistics

Display a breakdown of all spawners by type.

```java
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerStats {

    public void printStats(CommandSender sender, SmartSpawnerAPI api) {
        List<SpawnerDataDTO> all = api.getAllSpawners();

        Map<EntityType, Integer> counts = new HashMap<>();
        int totalStack = 0;

        for (SpawnerDataDTO s : all) {
            counts.merge(s.getEntityType(), 1, Integer::sum);
            totalStack += s.getStackSize();
        }

        sender.sendMessage("§6=== Spawner Statistics ===");
        sender.sendMessage("§eTotal blocks: §f" + all.size());
        sender.sendMessage("§eTotal stacked: §f" + totalStack);

        counts.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> sender.sendMessage("  §7- §f" + e.getKey() + " §7x§e" + e.getValue()));
    }
}
```

## Example 4: Double XP Event Listener

Double the XP players receive when claiming from spawners.

```java
import github.nighter.smartspawner.api.events.SpawnerExpClaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DoubleXPListener implements Listener {

    @EventHandler
    public void onExpClaim(SpawnerExpClaimEvent event) {
        event.setExpAmount(event.getExpAmount() * 2);
    }
}
```
