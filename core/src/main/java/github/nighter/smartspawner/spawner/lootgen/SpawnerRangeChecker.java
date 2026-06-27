package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpawnerRangeChecker {
    private static final long CHECK_INTERVAL = 20L; // 1 second in ticks
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ExecutorService executor;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SmartSpawner-RangeCheck"));
        initializeRangeCheckTask();
    }

    private void initializeRangeCheckTask() {
        // Using the global scheduler, but only for coordinating region-specific checks
        Scheduler.runTaskTimer(this::scheduleRegionSpecificCheck, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void scheduleRegionSpecificCheck() {
        PlayerRangeWrapper[] rangePlayers = getRangePlayers();

        this.executor.execute(() -> {
            final List<SpawnerData> allSpawners = spawnerManager.getAllSpawners();

            final RangeMath rangeCheck = new RangeMath(rangePlayers, allSpawners);
            final boolean[] spawnersPlayerFound = rangeCheck.getActiveSpawners();

            for (int i = 0; i < spawnersPlayerFound.length; i++) {
                final boolean expectedStop = !spawnersPlayerFound[i];
                final SpawnerData sd = allSpawners.get(i);
                final String spawnerId = sd.getSpawnerId();

                // Atomically update spawner stop flag only if it has changed
                if (sd.getSpawnerStop().compareAndSet(!expectedStop, expectedStop)) {
                    // Schedule main-thread task for actual state change
                    Scheduler.runLocationTask(sd.getSpawnerLocation(), () -> {
                        if (!isSpawnerValid(sd)) {
                            cleanupRemovedSpawner(spawnerId);
                            return;
                        }

                        // Double-check atomic boolean before applying
                        if (sd.getSpawnerStop().get() == expectedStop) {
                            handleSpawnerStateChange(sd, expectedStop);
                        }
                    });
                } else {
                    // Spawner state hasn't changed, but check if it's time to spawn loot
                    // Only process active spawners that are not stopped
                    if (sd.getSpawnerActive() && !sd.getSpawnerStop().get()) {
                        checkAndSpawnLoot(sd);
                    }
                }
            }
        });
    }

    private PlayerRangeWrapper[] getRangePlayers() {
        final Player[] onlinePlayers = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        final PlayerRangeWrapper[] rangePlayers = new PlayerRangeWrapper[onlinePlayers.length];
        int i = 0;

        for (Player p : onlinePlayers) {

            boolean conditions = p.isConnected() && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;

            // Store data in wrapper for faster access
            rangePlayers[i++] = new PlayerRangeWrapper(p.getWorld().getUID(),
                    p.getX(), p.getY(), p.getZ(),
                    conditions
            );
        }

        return rangePlayers;
    }

    private boolean isSpawnerValid(SpawnerData spawner) {
        // Check 1: Still in manager?
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        if (current == null) {
            return false;
        }

        // Check 2: Same instance? (prevents processing stale copies)
        if (current != spawner) {
            return false;
        }

        // Check 3: Location still valid?
        Location loc = spawner.getSpawnerLocation();
        return loc != null && loc.getWorld() != null;
    }

    private void cleanupRemovedSpawner(String spawnerId) {
        // Clear any pre-generated loot when spawner is removed
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner != null) {
            spawner.clearPreGeneratedLoot();
        }
    }

    private void handleSpawnerStateChange(SpawnerData spawner, boolean shouldStop) {
        if (!shouldStop) {
            activateSpawner(spawner);
        } else {
            deactivateSpawner(spawner);
        }

        // Force GUI update when spawner state changes
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().forceStateChangeUpdate(spawner);
        }
    }

    public void activateSpawner(SpawnerData spawner) {
        deactivateSpawner(spawner);

        // Check if spawner is actually active before starting
        if (!spawner.getSpawnerActive()) {
            return;
        }

        // Set lastSpawnTime to current time to start countdown immediately
        long currentTime = System.currentTimeMillis();
        spawner.setLastSpawnTime(currentTime);

        // Immediately update any open GUIs to show the countdown
        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
        }
    }

    public void deactivateSpawner(SpawnerData spawner) {
        // Clear any pre-generated loot when deactivating
        spawner.clearPreGeneratedLoot();
    }

    /**
     * Checks if a spawner should spawn loot based on its timer and spawns if needed.
     * This runs independently of GUI updates to ensure loot spawns even when no one is viewing.
     *
     * @param spawner The spawner to check
     */
    private void checkAndSpawnLoot(SpawnerData spawner) {
        // Calculate spawn delay
        long cachedDelay = spawner.getCachedSpawnDelay();
        if (cachedDelay == 0) {
            cachedDelay = (spawner.getSpawnDelay() + 20L) * 50L; // Convert ticks to milliseconds
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        final long finalCachedDelay = cachedDelay; // Make effectively final for lambda

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long timeElapsed = currentTime - lastSpawnTime;

        // Check if it's time to spawn loot
        if (timeElapsed >= cachedDelay) {
            // Try to acquire lock with short timeout to avoid blocking
            try {
                if (spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    try {
                        // Double-check time and state after acquiring lock
                        currentTime = System.currentTimeMillis();
                        lastSpawnTime = spawner.getLastSpawnTime();
                        timeElapsed = currentTime - lastSpawnTime;

                        if (timeElapsed >= cachedDelay && spawner.getSpawnerActive() && !spawner.getSpawnerStop().get()) {
                            Location spawnerLocation = spawner.getSpawnerLocation();
                            if (spawnerLocation != null) {
                                // Schedule loot spawning on the correct region thread
                                Scheduler.runLocationTask(spawnerLocation, () -> {
                                    // Final check before spawning
                                    if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                                        spawner.clearPreGeneratedLoot();
                                        return;
                                    }

                                    // Check if loot was already added early (for smooth UX)
                                    // If so, just update the timer without spawning again
                                    long timeSinceLastSpawn = System.currentTimeMillis() - spawner.getLastSpawnTime();
                                    if (timeSinceLastSpawn < finalCachedDelay - 100) { // 100ms tolerance
                                        // Loot was already added early, just update GUI
                                        if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                                            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
                                        }
                                        return;
                                    }

                                    // Spawn loot (pre-generated if available, otherwise generate new)
                                    if (spawner.hasPreGeneratedLoot()) {
                                        Map<ItemSignature, Long> items = spawner.getAndClearPreGeneratedItems();
                                        long exp = spawner.getAndClearPreGeneratedExperience();
                                        plugin.getSpawnerLootGenerator().addPreGeneratedLoot(spawner, items, exp);
                                    } else {
                                        plugin.getSpawnerLootGenerator().spawnLootToSpawner(spawner);
                                    }

                                    // Update last spawn time is handled by addPreGeneratedLoot/spawnLootToSpawner

                                    // Update any open GUIs to show the new loot
                                    if (plugin.getSpawnerGuiViewManager().hasViewers(spawner)) {
                                        plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);
                                    }
                                });
                            }
                        }
                    } finally {
                        spawner.getDataLock().unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void cleanup() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
