package github.nighter.smartspawner.spawner.natural;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class NaturalSpawnerListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;

    public NaturalSpawnerListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
    }

    // Improve server performance and ram allocation for spawners
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreSpawnerSpawn(PreSpawnerSpawnEvent event) {
        SpawnerData smartSpawner = spawnerManager.getSpawnerByLocation(event.getSpawnerLocation());

        if (smartSpawner != null) {
            event.setCancelled(true);
            event.setShouldAbortSpawn(true);
        } else {
            // This is a natural spawner - check if natural spawning is allowed
            if (!plugin.getConfig().getBoolean("natural_spawner.spawn_mobs", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() == null) return;

        SpawnerData smartSpawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());

        if (smartSpawner != null) {
            event.setCancelled(true);
        } else {
            // This is a natural spawner - check if natural spawning is allowed
            if (!plugin.getConfig().getBoolean("natural_spawner.spawn_mobs", true)) {
                event.setCancelled(true);
            }
        }
    }

}