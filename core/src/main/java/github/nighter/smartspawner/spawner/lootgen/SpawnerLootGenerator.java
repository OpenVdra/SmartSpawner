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
import java.util.concurrent.atomic.AtomicInteger;

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
            final AtomicInteger usedSlots;
            final AtomicInteger maxSlots;

            try {
                // Timing is now managed by SpawnerRangeChecker (timer) and SpawnerGuiViewManager (spawn trigger)
                // No need for time check here since spawn is only called when timer expires

                // Get exact inventory slot usage
                usedSlots = new AtomicInteger(spawner.getVirtualInventory().getUsedSlots());
                maxSlots = new AtomicInteger(spawner.getMaxSpawnerLootSlots());

                // Check if both inventory and exp are full, only then skip loot generation
                if (usedSlots.get() >= maxSlots.get() && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
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

                        // Re-check max slots as it could have changed
                        maxSlots.set(spawner.getMaxSpawnerLootSlots());
                        usedSlots.set(spawner.getVirtualInventory().getUsedSlots());

                        // Process items if there are any to add and inventory isn't completely full
                        if (!loot.items().isEmpty() && usedSlots.get() < maxSlots.get()) {
                            List<ItemStack> itemsToAdd = new ArrayList<>(loot.items());

                            // Get exact calculation of slots with the new items
                            int totalRequiredSlots = calculateRequiredSlots(itemsToAdd, spawner.getVirtualInventory());

                            // If we'll exceed the limit, limit the items we're adding
                            if (totalRequiredSlots > maxSlots.get()) {
                                itemsToAdd = limitItemsToAvailableSlots(itemsToAdd, spawner);
                            }

                            if (!itemsToAdd.isEmpty()) {
                                spawner.addItemsAndUpdateSellValue(itemsToAdd);
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
        List<LootItem> validItems =  spawner.getValidLootItems();

        if (validItems.isEmpty()) {
            return new LootResult(Collections.emptyList(), totalExperience);
        }

        // Use a Map to consolidate identical drops instead of List
        Map<ItemStack, Integer> consolidatedLoot = new HashMap<>();

        // Process mobs in batch rather than individually
        for (LootItem lootItem : validItems) {
            // Calculate the probability for the entire mob batch at once
            int totalAmount;

            if (Config.get().isApproximateLoot() && shouldApproximate(lootItem.chance(), mobCount)) {
                // O(1) binomial approximation
                totalAmount = generateApproximatedLoot(lootItem, mobCount);
            } else {
                // O(n) binomial distribution
                totalAmount = generateExactLoot(lootItem, mobCount);
            }

            if (totalAmount > 0) {
                // Create item just once per loot type
                ItemStack prototype = lootItem.createItemStack();
                if (prototype != null) {
                    consolidatedLoot.merge(prototype, totalAmount, Integer::sum);
                }
            }
        }

        // Convert consolidated map to item stacks
        List<ItemStack> finalLoot = new ArrayList<>(consolidatedLoot.size());
        for (Map.Entry<ItemStack, Integer> entry : consolidatedLoot.entrySet()) {
            ItemStack item = entry.getKey().clone();
            item.setAmount(Math.min(entry.getValue(), item.getMaxStackSize()));
            finalLoot.add(item);

            // Handle amounts exceeding max stack size
            int remaining = entry.getValue() - item.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extraStack = item.clone();
                extraStack.setAmount(Math.min(remaining, item.getMaxStackSize()));
                finalLoot.add(extraStack);
                remaining -= extraStack.getAmount();
            }
        }

        return new LootResult(finalLoot, totalExperience);
    }

    // Determines whether to use expected-value approximation
    private boolean shouldApproximate(double chance, int mobCount) {
        // simple heuristic: use expected if at least threshold items can be generated
        if (chance <= 0D) return false;
        return mobCount > (97.5D / chance) * Config.get().getApproximationThreshold();
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

    private List<ItemStack> limitItemsToAvailableSlots(List<ItemStack> items, SpawnerData spawner) {
        VirtualInventory currentInventory = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();

        // If already full, return empty list
        if (currentInventory.getUsedSlots() >= maxSlots) {
            return Collections.emptyList();
        }

        // Create a simulation inventory
        Map<ItemSignature, Long> simulatedInventory = new HashMap<>(currentInventory.getConsolidatedItems());
        List<ItemStack> acceptedItems = new ArrayList<>();

        // Sort items by priority (you can change this sorting strategy)
        items.sort(Comparator.comparing(item -> item.getType().name()));

        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            // Add to simulation and check slot count
            Map<ItemSignature, Long> tempSimulation = new HashMap<>(simulatedInventory);
            // Use cached signature to avoid excessive cloning
            ItemSignature sig = VirtualInventory.getSignature(item);
            tempSimulation.merge(sig, (long) item.getAmount(), (a, b) -> a + b);

            // Calculate slots needed
            int slotsNeeded = calculateSlots(tempSimulation);

            // If we still have room, accept this item
            if (slotsNeeded <= maxSlots) {
                acceptedItems.add(item);
                simulatedInventory = tempSimulation; // Update simulation
            } else {
                // Try to accept a partial amount of this item
                int maxStackSize = item.getMaxStackSize();
                long currentAmount = simulatedInventory.getOrDefault(sig, 0L);

                // Calculate how many we can add without exceeding slot limit
                int remainingSlots = maxSlots - calculateSlots(simulatedInventory);
                if (remainingSlots > 0) {
                    // Maximum items we can add in the remaining slots
                    long maxAddAmount = (long) remainingSlots * maxStackSize - (currentAmount % maxStackSize);
                    if (maxAddAmount > 0) {
                        // Create a partial item
                        ItemStack partialItem = item.clone();
                        partialItem.setAmount((int) Math.min(maxAddAmount, item.getAmount()));
                        acceptedItems.add(partialItem);

                        // Update simulation
                        simulatedInventory.merge(sig, (long) partialItem.getAmount(), (a, b) -> a + b);
                    }
                }

                // We've filled all slots, stop processing
                break;
            }
        }

        return acceptedItems;
    }

    private int calculateSlots(Map<ItemSignature, Long> items) {
        // Use a more efficient calculation approach
        return items.entrySet().stream()
                .mapToInt(entry -> {
                    long amount = entry.getValue();
                    int maxStackSize = entry.getKey().getMaxStackSize();
                    // Use integer division with ceiling function
                    return (int) ((amount + maxStackSize - 1) / maxStackSize);
                })
                .sum();
    }

    private int calculateRequiredSlots(List<ItemStack> items, VirtualInventory inventory) {
        // Create a temporary map to simulate how items would stack
        Map<ItemSignature, Long> simulatedItems = new HashMap<>();

        // First, get existing items if we need to account for them
        if (inventory != null) {
            simulatedItems.putAll(inventory.getConsolidatedItems());
        }

        // Add the new items to our simulation
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            // Use cached signature to avoid excessive cloning
            ItemSignature sig = VirtualInventory.getSignature(item);
            simulatedItems.merge(sig, (long) item.getAmount(), (a, b) -> a + b);
        }

        // Calculate exact slots needed
        return calculateSlots(simulatedItems);
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
            callback.onLootGenerated(Collections.emptyList(), 0);
            return;
        }

        try {
            try {
                if (!spawner.getDataLock().tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    callback.onLootGenerated(Collections.emptyList(), 0);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onLootGenerated(Collections.emptyList(), 0);
                return;
            }

            final int minMobs;
            final int maxMobs;
            final boolean itemStorageFull;

            try {
                int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                int maxSlots = spawner.getMaxSpawnerLootSlots();
                itemStorageFull = usedSlots >= maxSlots;
                boolean atCapacity = itemStorageFull && spawner.getSpawnerExp() >= spawner.getMaxStoredExp();

                if (atCapacity) {
                    callback.onLootGenerated(Collections.emptyList(), 0);
                    return;
                }

                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
            } finally {
                spawner.getDataLock().unlock();
            }

            Scheduler.runTaskAsync(() -> {
                LootResult loot;
                if (itemStorageFull) {
                    loot = generateExperienceOnlyLoot(minMobs, maxMobs, spawner);
                } else {
                    loot = generateLoot(minMobs, maxMobs, spawner);
                }

                callback.onLootGenerated(
                        loot.items() != null ? new ArrayList<>(loot.items()) : Collections.emptyList(),
                        loot.experience()
                );
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }

    private LootResult generateExperienceOnlyLoot(int minMobs, int maxMobs, SpawnerData spawner) {
        int mobCount = ThreadLocalRandom.current().nextInt(maxMobs - minMobs + 1) + minMobs;
        long totalExperienceLong = (long) spawner.getEntityExperienceValue() * mobCount;
        long totalExperience = Math.min(totalExperienceLong, Long.MAX_VALUE);
        return new LootResult(Collections.emptyList(), totalExperience);
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
     * @param items Pre-generated items list
     * @param experience Pre-generated experience amount
     */
    public void addPreGeneratedLoot(SpawnerData spawner, List<ItemStack> items, long experience) {
        addPreGeneratedLoot(spawner, items, experience, System.currentTimeMillis());
    }

    /**
     * Adds pre-generated loot to spawner with custom spawn time.
     * Used for early loot addition to prevent timer stutter.
     *
     * @param spawner The spawner to add loot to
     * @param items Pre-generated items list
     * @param experience Pre-generated experience amount
     * @param spawnTime The spawn time to set (for timer accuracy)
     */
    public void addPreGeneratedLoot(SpawnerData spawner, List<ItemStack> items, long experience, long spawnTime) {
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
                        List<ItemStack> validItems = new ArrayList<>();
                        for (ItemStack item : items) {
                            if (item != null && item.getType() != Material.AIR) {
                                validItems.add(item.clone());
                            }
                        }

                        if (!validItems.isEmpty()) {
                            int usedSlots = spawner.getVirtualInventory().getUsedSlots();
                            int maxSlots = spawner.getMaxSpawnerLootSlots();

                            if (usedSlots < maxSlots) {
                                List<ItemStack> itemsToAdd = validItems;

                                int totalRequiredSlots = calculateRequiredSlots(itemsToAdd, spawner.getVirtualInventory());
                                if (totalRequiredSlots > maxSlots) {
                                    itemsToAdd = limitItemsToAvailableSlots(itemsToAdd, spawner);
                                }

                                if (!itemsToAdd.isEmpty()) {
                                    spawner.addItemsAndUpdateSellValue(itemsToAdd);
                                    changed = true;
                                }
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
         * @param items Generated items list (never null, may be empty)
         * @param experience Generated experience amount
         */
        void onLootGenerated(List<ItemStack> items, long experience);
    }
}
