package github.nighter.smartspawner.spawner.gui.synchronization.utils;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for pre-generating loot to improve performance.
 * Handles the timing and execution of loot pre-generation based on spawn timers.
 */
public final class LootPreGenerationHelper {

    private static final long PRE_GENERATION_THRESHOLD = 2000L; // 2 seconds - start pre-generating loot
    private static final long EARLY_SPAWN_THRESHOLD = 1000L; // 1 second - add loot early for smooth UX

    private final SmartSpawner plugin;

    public LootPreGenerationHelper(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if loot should be pre-generated based on remaining time until spawn.
     *
     * @param timeUntilNextSpawn Time remaining in milliseconds
     * @return true if loot should be pre-generated
     */
    public boolean shouldPreGenerateLoot(long timeUntilNextSpawn) {
        return timeUntilNextSpawn > 0 && timeUntilNextSpawn <= PRE_GENERATION_THRESHOLD;
    }

    /**
     * Checks if pre-generated loot should be added early to storage.
     *
     * @param timeUntilNextSpawn Time remaining in milliseconds
     * @return true if loot should be added early
     */
    public boolean shouldAddLootEarly(long timeUntilNextSpawn) {
        return timeUntilNextSpawn > 0 && timeUntilNextSpawn <= EARLY_SPAWN_THRESHOLD;
    }

    /**
     * Triggers pre-generation of loot for a spawner asynchronously.
     *
     * @param spawner The spawner data
     */
    public void preGenerateLoot(SpawnerData spawner) {
        if (spawner.isPreGenerating() || spawner.hasPreGeneratedLoot()) {
            return;
        }

        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
            return;
        }

        spawner.setPreGenerating(true);

        Location spawnerLocation = spawner.getSpawnerLocation();
        if (spawnerLocation != null) {
            Scheduler.runLocationTask(spawnerLocation, () -> {
                if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                    spawner.setPreGenerating(false);
                    return;
                }

                plugin.getSpawnerLootGenerator().preGenerateLoot(spawner, (items, experience) -> {
                    spawner.storePreGeneratedLoot(items, experience);
                    spawner.setPreGenerating(false);
                });
            });
        }
    }

    /**
     * Adds pre-generated loot to spawner early for smooth UX.
     * This prevents flicker when timer resets.
     *
     * @param spawner The spawner data
     * @param cachedDelay The cached spawn delay
     */
    public void addPreGeneratedLootEarly(SpawnerData spawner, long cachedDelay) {
        if (!spawner.hasPreGeneratedLoot()) {
            return;
        }

        try {
            if (spawner.getDataLock().tryLock(100, TimeUnit.MILLISECONDS)) {
                try {
                    // Double-check time hasn't changed
                    long currentTime = System.currentTimeMillis();
                    long lastSpawnTime = spawner.getLastSpawnTime();
                    long timeElapsed = currentTime - lastSpawnTime;
                    long remainingTime = cachedDelay - timeElapsed;

                    // Only spawn if still within early threshold
                    if (remainingTime > 0 && remainingTime <= EARLY_SPAWN_THRESHOLD) {
                        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                            spawner.clearPreGeneratedLoot();
                            return;
                        }

                        Location spawnerLocation = spawner.getSpawnerLocation();
                        if (spawnerLocation != null) {
                            // Calculate when the loot should have spawned (for timer accuracy)
                            final long scheduledSpawnTime = lastSpawnTime + cachedDelay;

                            Scheduler.runLocationTask(spawnerLocation, () -> {
                                if (!spawner.getSpawnerActive() || spawner.getSpawnerStop().get()) {
                                    spawner.clearPreGeneratedLoot();
                                    return;
                                }

                                if (spawner.hasPreGeneratedLoot()) {
                                    Map<ItemSignature, Long> items = spawner.getAndClearPreGeneratedItems();
                                    long exp = spawner.getAndClearPreGeneratedExperience();

                                    // Add the loot with scheduled spawn time for accurate timer reset
                                    plugin.getSpawnerLootGenerator().addPreGeneratedLoot(spawner, items, exp, scheduledSpawnTime);
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
