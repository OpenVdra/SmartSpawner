package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.config.Config;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerLootGenerator {
    private final SmartSpawner plugin;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final SpawnerManager spawnerManager;

    public SpawnerLootGenerator(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    public void spawnLootToSpawner(SpawnerData spawner) {
        // Skip loot generation while a sell is in progress to avoid inventory conflicts
        if (spawner.isSelling()) {
            return;
        }

        // Try to acquire the lock, but don't block if it's already locked
        // This ensures we don't block the server thread while waiting for the lock
        boolean lockAcquired = spawner.getLootGenerationLock().tryLock();
        if (!lockAcquired) {
            // Lock is already held, which means another loot generation is happening
            // Skip this loot generation cycle
            return;
        }

        try {
            // Acquire dataLock to safely read spawn timing and configuration values
            // Use tryLock with short timeout to avoid blocking
            try {
                if (!spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    // dataLock is held (likely stack size change), skip this cycle
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Declare variables outside the try block so they're accessible in the async lambda
            final long currentTime = System.currentTimeMillis();
            final long spawnTime;
            final int minMobs;
            final int maxMobs;

            try {
                // Timing is now managed by SpawnerRangeChecker (timer) and SpawnerGuiViewManager (spawn trigger)
                // No need for time check here since spawn is only called when timer expires

                // Get exact inventory slot usage
                int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                int maxSlots = spawner.getMaxSpawnerLootSlots();

                // Check if both inventory and exp are full, only then skip loot generation
                if (usedSlots >= maxSlots && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
                    if (!spawner.getIsAtCapacity()) {
                        spawner.setIsAtCapacity(true);
                    }
                    return; // Skip generation if both exp and inventory are full
                }

                // Important: Store the current values we need for async processing
                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
                // Store currentTime to update lastSpawnTime after successful loot addition
                spawnTime = currentTime;
            } finally {
                spawner.getDataLock().unlock();
            }

            // Run heavy calculations async and batch updates using the Scheduler
            Scheduler.runTaskAsync(() -> {
                // Generate loot with full mob count
                LootResult loot = generateLoot(minMobs, maxMobs, spawner);

                // Only proceed if we generated something
                if (loot.items().isEmpty() && loot.experience() == 0) {
                    return;
                }

                // Switch back to main thread for Bukkit API calls using location-aware scheduling
                Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
                    // Re-acquire the lock for the update phase
                    // This ensures the spawner hasn't been modified (like stack size changes)
                    // between our async calculations and now
                    boolean updateLockAcquired = spawner.getLootGenerationLock().tryLock();
                    if (!updateLockAcquired) {
                        // Lock is held, stack size is changing, skip this update
                        return;
                    }

                    try {
                        // Modified approach: Handle items and exp separately
                        boolean changed = false;

                        // Process experience if there's any to add and not at max
                        if (loot.experience() > 0 && spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
                            long currentExp = spawner.getSpawnerExp();
                            long maxExp = spawner.getMaxStoredExp();
                            long newExpLong = (long) currentExp + loot.experience();
                            long newExp = Math.min(newExpLong, maxExp);

                            if (newExp != currentExp) {
                                spawner.setSpawnerExp(newExp);
                                changed = true;
                            }
                        }

                        if (!loot.items().isEmpty()) {
                            Map<ItemSignature, Long> lootToAdd = loot.items();
                            int maxSlots = spawner.getMaxSpawnerLootSlots();

                            int totalRequiredSlots = calculateRequiredSlots(lootToAdd, spawner.getVirtualInventory());
                            if (totalRequiredSlots > maxSlots) {
                                lootToAdd = limitLootToAvailableSlots(lootToAdd, spawner);
                            }

                            if (!lootToAdd.isEmpty()) {
                                spawner.addItemsAndUpdateSellValue(lootToAdd);
                                changed = true;
                            }
                        }

                        if (!changed) {
                            return;
                        }

                        // Update spawn time only after successful loot addition
                        // This prevents skipped spawns when the lock fails
                        // Must acquire dataLock to safely update lastSpawnTime
                        boolean updateDataLockAcquired = spawner.getDataLock().tryLock();
                        if (updateDataLockAcquired) {
                            try {
                                spawner.setLastSpawnTime(spawnTime);
                            } finally {
                                spawner.getDataLock().unlock();
                            }
                        }

                        // Check if spawner is now at capacity and update status if needed
                        spawner.updateCapacityStatus();

                        // Handle GUI updates in batches
                        handleGuiUpdates(spawner);

                        // Mark for saving only once
                        spawnerManager.markSpawnerModified(spawner.getSpawnerId());
                    } finally {
                        spawner.getLootGenerationLock().unlock();
                    }
                });
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }

    public LootResult generateLoot(int minMobs, int maxMobs, SpawnerData spawner) {
        int mobCount = ThreadLocalRandom.current().nextInt(maxMobs - minMobs + 1) + minMobs;
        long totalExperience = (long) spawner.getEntityExperienceValue() * mobCount;

        // Get valid items from the spawner's EntityLootConfig
        List<LootItem> validItems = spawner.getValidLootItems();

        if (validItems.isEmpty()) {
            return new LootResult(Collections.emptyMap(), totalExperience);
        }

        // Use a Map to consolidate identical drops instead of List
        Map<ItemSignature, Long> consolidatedLoot = new HashMap<>();

        boolean shouldApproximateLoot = Config.get().isApproximateLoot();
        int approximationThreshold = Config.get().getApproximationThreshold();

        // Process mobs in batch rather than individually
        for (LootItem lootItem : validItems) {
            long totalAmount;

            if (shouldApproximateLoot && shouldApproximate(lootItem.chance(), mobCount, approximationThreshold)) {
                totalAmount = generateApproximatedLoot(lootItem, mobCount);
            } else {
                totalAmount = generateExactLoot(lootItem, mobCount);
            }

            if (totalAmount <= 0) {
                continue;
            }

            ItemStack prototype = lootItem.createItemStack();
            if (prototype == null || prototype.getType() == Material.AIR) {
                continue;
            }

            ItemSignature signature = VirtualInventory.getSignature(prototype);
            consolidatedLoot.merge(signature, totalAmount, Long::sum);
        }

        return new LootResult(consolidatedLoot, totalExperience);
    }

    // Determines whether to use expected-value approximation
    private boolean shouldApproximate(double chance, int mobCount, int approximationThreshold) {
        // simple heuristic: use expected if at least threshold items can be generated
        if (chance <= 0D) return false;
        return mobCount > (97.5D / chance) * approximationThreshold;
    }

    // O(n) simulation: exact per-mob drop calculation
    private int generateExactLoot(LootItem lootItem, int mobCount) {
        int successfulDrops = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double p = lootItem.chance() / 100.0;
        for (int i = 0; i < mobCount; i++) {
            if (random.nextDouble() < p) {
                successfulDrops++;
            }
        }
        int totalAmount = 0;
        for (int i = 0; i < successfulDrops; i++) {
            totalAmount += lootItem.generateAmount(random);
        }
        return totalAmount;
    }

    // O(1) expected-value calculation with small jitter
    private int generateApproximatedLoot(LootItem lootItem, int mobCount) {
        double p = lootItem.chance() / 100.0;
        double expectedDrops = mobCount * p;
        double avgAmount = lootItem.getAverageAmount();
        double jitter = p != 1.0
                ? 0.95 + ThreadLocalRandom.current().nextDouble() * 0.10
                : 1.0;
        return (int) Math.round(expectedDrops * avgAmount * jitter);
    }

    private Map<ItemSignature, Long> limitLootToAvailableSlots(Map<ItemSignature, Long> loot, SpawnerData spawner) {
        VirtualInventory inventory = spawner.getVirtualInventory();

        int maxSlots = spawner.getMaxSpawnerLootSlots();

        if (maxSlots <= 0) {
            return Collections.emptyMap();
        }

        Map<ItemSignature, Long> simulatedInventory = new HashMap<>(inventory.getConsolidatedItems());
        Map<ItemSignature, Long> acceptedLoot = new HashMap<>(loot.size());

        int usedSlots = calculateSlots(simulatedInventory);

        List<Map.Entry<ItemSignature, Long>> entries = new ArrayList<>(loot.entrySet());

        entries.sort(Comparator.comparing(entry -> entry.getKey().getMaterial().name()));

        for (Map.Entry<ItemSignature, Long> entry : entries) {
            ItemSignature signature = entry.getKey();

            long amount = entry.getValue();

            int maxStackSize = signature.getMaxStackSize();

            long currentAmount = simulatedInventory.getOrDefault(signature, 0L);

            int oldSlots = slotsFor(currentAmount, maxStackSize);
            int newSlots = slotsFor(currentAmount + amount, maxStackSize);

            int slotDelta = newSlots - oldSlots;

            if (usedSlots + slotDelta <= maxSlots) {
                acceptedLoot.put(signature, amount);

                simulatedInventory.put(signature, currentAmount + amount);

                usedSlots += slotDelta;

                continue;
            }

            int remainingSlots = Math.max(0, maxSlots - usedSlots);
            long maxAddAmount = ((long) (oldSlots + remainingSlots) * maxStackSize) - currentAmount;

            if (maxAddAmount <= 0) {
                continue;
            }

            long acceptedAmount = (int) Math.min(maxAddAmount, amount);

            if (acceptedAmount > 0) {
                acceptedLoot.put(signature, acceptedAmount);
                simulatedInventory.put(signature, currentAmount + acceptedAmount);
                usedSlots = calculateSlots(simulatedInventory);
            }
        }

        return acceptedLoot;
    }

    private int calculateRequiredSlots(Map<ItemSignature, Long> loot, VirtualInventory inventory) {
        Map<ItemSignature, Long> simulatedItems = new HashMap<>(inventory.getConsolidatedItems());

        for (Map.Entry<ItemSignature, Long> entry : loot.entrySet()) {
            simulatedItems.merge(entry.getKey(), (long) entry.getValue(), Long::sum);
        }

        return calculateSlots(simulatedItems);
    }

    private int calculateSlots(Map<ItemSignature, Long> items) {
        int total = 0;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            total += slotsFor(entry.getValue(), entry.getKey().getMaxStackSize());
        }

        return total;
    }

    private int slotsFor(long amount, int maxStackSize) {
        if (amount <= 0) {
            return 0;
        }

        return (int) ((amount + maxStackSize - 1) / maxStackSize);
    }

    private Map<ItemSignature, Long> copyLoot(Map<ItemSignature, Long> loot) {
        if (loot == null || loot.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ItemSignature, Long> copy = new HashMap<>(loot.size());
        for (Map.Entry<ItemSignature, Long> entry : loot.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amount = entry.getValue();
            if (amount <= 0) {
                continue;
            }
            copy.merge(signature, amount, Long::sum);
        }

        return copy;
    }

    /**
     * Handle GUI updates after loot has been added to VirtualInventory.
     *
     * CRITICAL: This method is called while lootGenerationLock is held, which ensures:
     * 1. VirtualInventory is in a consistent state (loot has been added)
     * 2. No storage operations can interfere during GUI update dispatch
     * 3. All viewers will receive the updated state before any storage operations are allowed
     *
     * This guarantees that VirtualInventory remains the single source of truth.
     */
    private void handleGuiUpdates(SpawnerData spawner) {
        // Dispatch GUI updates to all viewers
        // Storage operations will be blocked until lootGenerationLock is released
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Show particles if needed
        if (Config.get().isSpawnerGenerateLootParticlesEnabled()) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();
            if (world != null) {
                Scheduler.runLocationTask(loc, () -> world.spawnParticle(Particle.HAPPY_VILLAGER,
                        loc.clone().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0));
            }
        }

        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            spawner.updateHologramData();
        }
    }

    /**
     * Pre-generates loot asynchronously for improved UX.
     * Loot is calculated in background before timer expires, then added instantly when ready.
     *
     * <p>This method:
     * <ul>
     *   <li>Checks spawner capacity before generation</li>
     *   <li>Generates loot asynchronously to avoid blocking</li>
     *   <li>Invokes callback with generated items and experience</li>
     *   <li>Handles thread-safety with proper locking</li>
     * </ul>
     *
     * @param spawner The spawner to pre-generate loot for
     * @param callback Callback invoked with generated loot (items, experience)
     */
    public void preGenerateLoot(SpawnerData spawner, LootGenerationCallback callback) {
        if (!spawner.getLootGenerationLock().tryLock()) {
            callback.onLootGenerated(Collections.emptyMap(), 0);
            return;
        }

        try {
            try {
                if (!spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    callback.onLootGenerated(Collections.emptyMap(), 0);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onLootGenerated(Collections.emptyMap(), 0);
                return;
            }

            final int minMobs;
            final int maxMobs;
            try {
                int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                int maxSlots = spawner.getMaxSpawnerLootSlots();
                boolean atCapacity = usedSlots >= maxSlots && spawner.getSpawnerExp() >= spawner.getMaxStoredExp();

                if (atCapacity) {
                    callback.onLootGenerated(Collections.emptyMap(), 0);
                    return;
                }

                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
            } finally {
                spawner.getDataLock().unlock();
            }

            Scheduler.runTaskAsync(() -> {
                LootResult loot = generateLoot(minMobs, maxMobs, spawner);

                callback.onLootGenerated(
                        copyLoot(loot.items()),
                        loot.experience()
                );
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }

    /**
     * Adds pre-generated loot to spawner instantly when timer expires.
     *
     * <p>This method:
     * <ul>
     *   <li>Validates pre-generated loot is not empty</li>
     *   <li>Rechecks capacity (may have changed since pre-generation)</li>
     *   <li>Adds items and experience to spawner</li>
     *   <li>Updates lastSpawnTime to maintain cycle timing</li>
     *   <li>Triggers GUI updates and marks spawner for persistence</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> All Bukkit API calls are scheduled on main thread via Scheduler.runLocationTask
     *
     * @param spawner The spawner to add loot to
     * @param items Pre-generated items map
     * @param experience Pre-generated experience amount
     */
    public void addPreGeneratedLoot(SpawnerData spawner, Map<ItemSignature, Long> items, long experience) {
        addPreGeneratedLoot(spawner, items, experience, System.currentTimeMillis());
    }

    /**
     * Adds pre-generated loot to spawner with custom spawn time.
     * Used for early loot addition to prevent timer stutter.
     *
     * @param spawner The spawner to add loot to
     * @param items Pre-generated items map
     * @param experience Pre-generated experience amount
     * @param spawnTime The spawn time to set (for timer accuracy)
     */
    public void addPreGeneratedLoot(SpawnerData spawner, Map<ItemSignature, Long> items, long experience, long spawnTime) {
        if ((items == null || items.isEmpty()) && experience == 0) {
            return;
        }

        Location spawnerLocation = spawner.getSpawnerLocation();
        if (spawnerLocation == null) {
            return;
        }

        Scheduler.runLocationTask(spawnerLocation, () -> {
            if (!spawner.getLootGenerationLock().tryLock()) {
                return;
            }

            try {
                try {
                    if (!spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                try {
                    int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                    int maxSlots = spawner.getMaxSpawnerLootSlots();
                    boolean isCompletelyFull = usedSlots >= maxSlots && spawner.getSpawnerExp() >= spawner.getMaxStoredExp();

                    if (isCompletelyFull) {
                        return;
                    }
                } finally {
                    spawner.getDataLock().unlock();
                }

                Scheduler.runTaskAsync(() -> {
                    boolean changed = false;

                    if (experience > 0 && spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
                        long currentExp = spawner.getSpawnerExp();
                        long maxExp = spawner.getMaxStoredExp();
                        long newExpLong = (long) currentExp + experience;
                        long newExp = Math.min(newExpLong, maxExp);

                        if (newExp != currentExp) {
                            spawner.setSpawnerExp(newExp);
                            changed = true;
                        }
                    }

                    if (items != null && !items.isEmpty()) {
                        Map<ItemSignature, Long> lootToAdd = copyLoot(items);

                        if (!lootToAdd.isEmpty()) {
                            int maxSlots = spawner.getMaxSpawnerLootSlots();

                            int totalRequiredSlots = calculateRequiredSlots(lootToAdd, spawner.getVirtualInventory());
                            if (totalRequiredSlots > maxSlots) {
                                lootToAdd = limitLootToAvailableSlots(lootToAdd, spawner);
                            }

                            if (!lootToAdd.isEmpty()) {
                                spawner.addItemsAndUpdateSellValue(lootToAdd);
                                changed = true;
                            }
                        }
                    }

                    if (!changed) {
                        return;
                    }

                    if (spawner.getDataLock().tryLock()) {
                        try {
                            spawner.setLastSpawnTime(spawnTime);
                        } finally {
                            spawner.getDataLock().unlock();
                        }
                    }

                    spawner.updateCapacityStatus();
                    handleGuiUpdates(spawner);
                    spawnerManager.markSpawnerModified(spawner.getSpawnerId());
                });
            } finally {
                spawner.getLootGenerationLock().unlock();
            }
        });
    }

    /**
     * Callback interface for asynchronous loot pre-generation.
     * Invoked when loot generation completes with the generated items and experience.
     */
    @FunctionalInterface
    public interface LootGenerationCallback {
        /**
         * Called when loot generation completes.
         *
         * @param items Generated items map (never null, may be empty)
         * @param experience Generated experience amount
         */
        void onLootGenerated(Map<ItemSignature, Long> items, long experience);
    }
}
