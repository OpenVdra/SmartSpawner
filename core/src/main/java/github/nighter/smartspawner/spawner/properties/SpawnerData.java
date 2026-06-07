package github.nighter.smartspawner.spawner.properties;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.sell.SellResult;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SpawnerData {
    @Getter
    private final SmartSpawner plugin;

    @Getter @Setter
    private String spawnerId;
    @Getter
    private final Location spawnerLocation;

    // Fine-grained locks for different operations (Lock Striping Pattern)
    @Getter
    private final ReentrantLock inventoryLock = new ReentrantLock();  // For storage operations
    @Getter
    private final ReentrantLock lootGenerationLock = new ReentrantLock();  // For loot spawning
    @Getter
    private final ReentrantLock dataLock = new ReentrantLock();  // For metadata changes (exp, stack size, etc.)

    // Atomic sell state – single CAS guard that replaces the old sellLock + double-lock pattern.
    // All operations that touch virtual inventory must check isSelling() before proceeding.
    private final AtomicBoolean selling = new AtomicBoolean(false);

    // Dirty flag for storage GUI – set when items are moved/dropped inside the storage GUI,
    // cleared (and spawner queued for save) when the GUI is closed or main menu is returned to.
    private final AtomicBoolean storageDirty = new AtomicBoolean(false);

    // Base values from config (immutable after load)
    @Getter
    private long baseMaxStoredExp;
    @Getter @Setter
    private int baseMaxStoragePages;
    @Getter @Setter
    private int baseMinMobs;
    @Getter @Setter
    private int baseMaxMobs;

    @Getter
    private long spawnerExp;
    @Getter @Setter
    private Boolean spawnerActive;
    @Getter @Setter
    private Integer spawnerRange;
    @Getter
    private AtomicBoolean spawnerStop;
    @Getter @Setter
    private Boolean isAtCapacity;
    @Getter @Setter
    private Long lastSpawnTime;
    @Getter
    private long spawnDelay;

    @Getter
    private EntityType entityType;
    @Getter @Setter
    private EntityLootConfig lootConfig;

    // Item spawner support - stores the material being spawned for item spawners
    @Getter @Setter
    private Material spawnedItemMaterial;

    // Calculated values based on stackSize
    @Getter
    private int maxStoragePages;
    @Getter @Setter
    private int maxSpawnerLootSlots;
    @Getter @Setter
    private long maxStoredExp;
    @Getter @Setter
    private int minMobs;
    @Getter @Setter
    private int maxMobs;

    @Getter
    private int stackSize;
    @Getter @Setter
    private int maxStackSize;

    @Getter @Setter
    private VirtualInventory virtualInventory;
    @Getter
    private final Set<Material> filteredItems = new HashSet<>();

    @Getter @Setter
    private String lastInteractedPlayer;

    @Getter
    private SellResult lastSellResult;
    @Getter
    private boolean lastSellProcessed;

    // Accumulated sell value for optimization
    @Getter
    private volatile double accumulatedSellValue;

    @Getter
    private volatile boolean sellValueDirty;

    private SpawnerHologram hologram;
    @Getter @Setter
    private long cachedSpawnDelay;

    // Sort preference for spawner storage
    @Getter @Setter
    private Material preferredSortItem;

    // CRITICAL: Pre-generated loot storage for better UX - access must be synchronized via lootGenerationLock
    private volatile List<ItemStack> preGeneratedItems;
    private volatile long preGeneratedExperience;
    private volatile boolean isPreGenerating;

    // Cache for no-loot detection to avoid repeated expensive checks
    private volatile Boolean cachedHasNoLoot = null;

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.spawnedItemMaterial = null;

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    // Constructor for item spawners
    public SpawnerData(String id, Location location, Material itemMaterial, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = EntityType.ITEM;
        this.spawnedItemMaterial = itemMaterial;

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    private void initializeDefaults() {
        this.spawnerExp = 0;
        this.spawnerActive = true;
        this.spawnerStop = new AtomicBoolean(true);
        this.isAtCapacity = false;
        this.stackSize = 1;
        this.lastSpawnTime = System.currentTimeMillis();
        this.preferredSortItem = null; // Initialize sort preference as null
        this.accumulatedSellValue = 0.0;
        this.sellValueDirty = true;
    }

    public void loadConfigurationValues() {
        this.baseMaxStoredExp = plugin.getConfig().getLong("spawner_properties.default.max_stored_exp", 1000L);
        this.baseMaxStoragePages = plugin.getConfig().getInt("spawner_properties.default.max_storage_pages", 1);
        this.baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        this.baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.spawnDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        this.cachedSpawnDelay = (this.spawnDelay + 20L) * 50L; // Add 1 second buffer for GUI display and convert tick to ms
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);

        // Load loot config based on spawner type
        if (isItemSpawner() && spawnedItemMaterial != null) {
            this.lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
    }

    public void recalculateAfterConfigReload() {
        calculateStackBasedValues();
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            recreateVirtualInventory();
        }
        // Mark sell value as dirty after config reload since prices may have changed
        this.sellValueDirty = true;
        updateHologramData();

        // Invalidate GUI cache after config reload
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    /**
     * Recalculates spawner values after API modifications.
     * Similar to {@link #recalculateAfterConfigReload()} but specifically for API changes.
     */
    public void recalculateAfterAPIModification() {
        calculateStackBasedValues();
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            recreateVirtualInventory();
        }
        updateHologramData();

        // Invalidate GUI cache after API modifications
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void calculateStackBasedValues() {
        this.maxStoredExp = clampToLong(baseMaxStoredExp * stackSize, 0L, Long.MAX_VALUE);
        this.maxStoragePages = clampToInt((long) baseMaxStoragePages * stackSize, 0, Integer.MAX_VALUE);
        this.maxSpawnerLootSlots = clampToInt((long) maxStoragePages * 45L, 0, Integer.MAX_VALUE);
        this.minMobs = clampToInt((long) baseMinMobs * stackSize, 0, Integer.MAX_VALUE);
        this.maxMobs = clampToInt((long) baseMaxMobs * stackSize, 0, Integer.MAX_VALUE);
        this.spawnerExp = clampToLong(this.spawnerExp, 0L, this.maxStoredExp);
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 500;
        long ticksWithBuffer = this.spawnDelay > Long.MAX_VALUE - 20L ? Long.MAX_VALUE : this.spawnDelay + 20L;
        this.cachedSpawnDelay = ticksWithBuffer > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticksWithBuffer * 50L;
        if (baseSpawnerDelay <= 0) {
            plugin.getLogger().warning("Invalid spawner delay value. Setting to default: 500 ticks (25s)");
        }
    }
    public void setSpawnDelayFromConfig() {
        long delay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        if (delay <= 0) {
            plugin.getLogger().warning("Invalid spawner delay value in config. Setting to default: 500 ticks (25s)");
            delay = 500L;
        }
        setSpawnDelay(delay);
    }

    private void initializeComponents() {
        this.virtualInventory = new VirtualInventory(maxSpawnerLootSlots);
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            createHologram();
        }

        if (this.preferredSortItem == null && this.lootConfig != null && this.lootConfig.getAllItems() != null) {
            var lootItems = this.lootConfig.getAllItems();
            if (!lootItems.isEmpty()) {
                var sortedLoot = lootItems.stream()
                        .map(LootItem::material)
                        .distinct()
                        .sorted(Comparator.comparing(Material::name))
                        .toList();

                if (!sortedLoot.isEmpty()) {
                    this.preferredSortItem = sortedLoot.getFirst();
                }
            }
        }
        this.virtualInventory.sortItems(this.preferredSortItem);
    }

    private void createHologram() {
        this.hologram = new SpawnerHologram(spawnerLocation);
        this.hologram.createHologram();
        updateHologramData();
    }

    public void setStackSize(int stackSize) {
        setStackSize(stackSize, true);
    }

    public void setStackSize(int stackSize, boolean restartHopper) {
        // Acquire locks in consistent order to prevent deadlocks:
        // 1. dataLock - for metadata changes
        // 2. inventoryLock - to prevent inventory operations during virtual inventory replacement
        // Note: We don't acquire lootGenerationLock here to avoid blocking loot generation cycles
        dataLock.lock();
        try {
            inventoryLock.lock();
            try {
                updateStackSize(stackSize, restartHopper);
            } finally {
                inventoryLock.unlock();
            }
        } finally {
            dataLock.unlock();
        }
    }

    private void updateStackSize(int newStackSize, boolean restartHopper) {
        if (newStackSize <= 0) {
            this.stackSize = 1;
            plugin.getLogger().warning("Invalid stack size. Setting to 1");
            return;
        }

        // Only prevent INCREASING beyond maxStackSize.
        // If the config limit was lowered after a spawner accumulated a higher stack,
        // we must still allow the count to decrease (e.g. on break) to avoid data loss.
        if (newStackSize > this.maxStackSize && newStackSize > this.stackSize) {
            plugin.getLogger().warning("Stack size " + newStackSize + " exceeds maximum " + this.maxStackSize + ". Ignoring.");
            return;
        }

        this.stackSize = newStackSize;
        calculateStackBasedValues();

        // Resize the existing virtual inventory instead of creating a new one
        virtualInventory.resize(this.maxSpawnerLootSlots);

        // Reset lastSpawnTime to prevent exploit where players break spawners to trigger immediate loot
        this.lastSpawnTime = System.currentTimeMillis();
        updateHologramData();

        // Invalidate GUI cache when stack size changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void recreateVirtualInventory() {
        if (virtualInventory == null) return;
        virtualInventory.resize(maxSpawnerLootSlots);
    }

    public void setSpawnerExp(long exp) {
        this.spawnerExp = Math.clamp(exp, 0L, maxStoredExp);
        updateHologramData();

        // Invalidate GUI cache when experience changes
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
        if (plugin.getSpawnerMenuFormUI() != null) {
            plugin.getSpawnerMenuFormUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    public void setSpawnerExpData(long exp) {
        this.spawnerExp = Math.max(0L, exp);
    }

    public void setBaseMaxStoredExp(long baseMaxStoredExp) {
        this.baseMaxStoredExp = Math.max(0L, baseMaxStoredExp);
    }

    private int clampToInt(long value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return (int) value;
    }

    private long clampToLong(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public void updateHologramData() {
        if (hologram != null) {
            hologram.updateData(stackSize, entityType, spawnerExp, maxStoredExp,
                    virtualInventory.getUsedSlots(), maxSpawnerLootSlots);
        }
    }

    public void reloadHologramData() {
        if (hologram != null) {
            hologram.remove();
            createHologram();
        }
    }

    public void refreshHologram() {
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            if (hologram == null) {
                createHologram();
            }
        } else if (hologram != null) {
            removeHologram();
        }
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    public boolean isCompletelyFull() {
        return virtualInventory.getUsedSlots() >= maxSpawnerLootSlots && spawnerExp >= maxStoredExp;
    }

    public boolean updateCapacityStatus() {
        boolean newStatus = isCompletelyFull();
        if (newStatus != isAtCapacity) {
            isAtCapacity = newStatus;
            return true;
        }
        return false;
    }

    public void setEntityType(EntityType newType) {
        this.entityType = newType;
        this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(newType);
        // Mark sell value as dirty since entity type and prices changed
        this.sellValueDirty = true;
        updateHologramData();
    }

    public boolean toggleItemFilter(Material material) {
        boolean wasFiltered = filteredItems.contains(material);
        if (wasFiltered) {
            filteredItems.remove(material);
        } else {
            filteredItems.add(material);
        }
        return !wasFiltered;
    }

    public List<LootItem> getValidLootItems() {
        if (lootConfig == null) {
            return Collections.emptyList();
        }
        return lootConfig.getAllItems().stream()
                .filter(this::isLootItemValid)
                .collect(Collectors.toList());
    }

    private boolean isLootItemValid(LootItem item) {
        ItemStack example = item.createItemStack();
        return example != null && !filteredItems.contains(example.getType());
    }

    public int getEntityExperienceValue() {
        return lootConfig != null ? lootConfig.experience() : 0;
    }

    /**
     * Checks if this spawner has any configured loot or experience.
     * Used to detect spawners that will never generate anything (like Allay).
     * Result is cached for performance.
     *
     * @return true if spawner has no loot items and no experience configured
     */
    public boolean hasNoLootOrExperience() {
        // Return cached value if available
        if (cachedHasNoLoot != null) {
            return cachedHasNoLoot;
        }

        // Calculate and cache the result
        boolean result = (lootConfig == null ||
                (lootConfig.experience() == 0 && getValidLootItems().isEmpty()));
        cachedHasNoLoot = result;
        return result;
    }

    public void setLootConfig() {
        // Load loot config based on spawner type
        if (isItemSpawner() && spawnedItemMaterial != null) {
            this.lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            this.lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
        // Mark sell value as dirty since prices may have changed
        this.sellValueDirty = true;
        // Invalidate no-loot cache since config changed
        this.cachedHasNoLoot = null;
    }

    public void setLastSellResult(SellResult sellResult) {
        this.lastSellResult = sellResult;
        this.lastSellProcessed = false;
    }

    public void markLastSellAsProcessed() {
        this.lastSellProcessed = true;
        this.lastSellResult = null;
    }

    /** @return true if this spawner is currently executing a sell operation */
    public boolean isSelling() {
        return selling.get();
    }

    /**
     * Atomically transitions the spawner into selling state.
     * @return true if the transition succeeded (caller owns the sell), false if already selling
     */
    public boolean startSelling() {
        return selling.compareAndSet(false, true);
    }

    /** Releases the selling state so other operations may proceed. */
    public void stopSelling() {
        selling.set(false);
    }

    /** @return true if the storage GUI content was modified since last save. */
    public boolean isStorageDirty() {
        return storageDirty.get();
    }

    /** Marks that the storage GUI content has been modified and needs to be saved. */
    public void markStorageDirty() {
        storageDirty.set(true);
    }

    /** Clears the storage dirty flag after the spawner has been queued for saving. */
    public void clearStorageDirty() {
        storageDirty.set(false);
    }

    public void updateLastInteractedPlayer(String playerName) {
        this.lastInteractedPlayer = playerName;
    }

    /**
     * Marks the sell value as dirty, requiring recalculation
     */
    public void markSellValueDirty() {
        this.sellValueDirty = true;
    }

    /**
     * Updates the accumulated sell value for specific items being added
     * @param itemsAdded Map of item signatures to quantities added
     * @param priceCache Price cache from loot config
     */
    public void incrementSellValue(Map<ItemSignature, Long> itemsAdded,
                                   Map<String, Double> priceCache) {
        if (itemsAdded == null || itemsAdded.isEmpty()) {
            return;
        }

        double addedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : itemsAdded.entrySet()) {
            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                addedValue += itemPrice * entry.getValue();
            }
        }

        this.accumulatedSellValue += addedValue;
        this.sellValueDirty = false;
    }

    /**
     * Decrements the accumulated sell value when items are removed
     * @param itemsRemoved List of items removed
     * @param priceCache Price cache from loot config
     */
    public void decrementSellValue(List<ItemStack> itemsRemoved, Map<String, Double> priceCache) {
        if (itemsRemoved == null || itemsRemoved.isEmpty()) {
            return;
        }

        // Consolidate removed items
        Map<ItemSignature, Long> consolidated = new java.util.HashMap<>();
        for (ItemStack item : itemsRemoved) {
            if (item == null || item.getAmount() <= 0) continue;
            // Use cached signature to avoid excessive cloning
            ItemSignature sig = VirtualInventory.getSignature(item);
            consolidated.merge(sig, (long) item.getAmount(), (a, b) -> a + b);
        }

        double removedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : consolidated.entrySet()) {
            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                removedValue += itemPrice * entry.getValue();
            }
        }

        this.accumulatedSellValue = Math.max(0.0, this.accumulatedSellValue - removedValue);
    }

    /**
     * Forces a full recalculation of the accumulated sell value
     * Should be called when the cache is dirty or on spawner load
     */
    public void recalculateSellValue() {
        if (lootConfig == null) {
            this.accumulatedSellValue = 0.0;
            this.sellValueDirty = false;
            return;
        }

        // Get price cache
        Map<String, Double> priceCache = createPriceCache();

        // Calculate from current inventory
        Map<ItemSignature, Long> items = virtualInventory.getConsolidatedItems();
        double totalValue = 0.0;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            double itemPrice = findItemPrice(entry.getKey(), priceCache);
            if (itemPrice > 0.0) {
                totalValue += itemPrice * entry.getValue();
            }
        }

        this.accumulatedSellValue = totalValue;
        this.sellValueDirty = false;
    }

    /**
     * Gets the price cache from loot config.
     * Prefers live prices from ItemPriceManager to avoid startup timing issues where
     * shop plugin prices aren't yet available when LootItem.sellPrice is baked in.
     */
    public Map<String, Double> createPriceCache() {
        if (lootConfig == null) {
            return new java.util.HashMap<>();
        }

        github.nighter.smartspawner.hooks.economy.ItemPriceManager priceManager = plugin.getItemPriceManager();
        Map<String, Double> cache = new java.util.HashMap<>();
        java.util.List<LootItem> allLootItems = lootConfig.getAllItems();

        for (LootItem lootItem : allLootItems) {
            // Use live price from ItemPriceManager; fall back to baked sellPrice if unavailable
            double price = (priceManager != null) ? priceManager.getPrice(lootItem.material()) : 0.0;
            if (price <= 0.0) {
                price = lootItem.sellPrice();
            }
            if (price > 0.0) {
                ItemStack template = lootItem.createItemStack();
                if (template != null) {
                    String key = createItemKey(template);
                    cache.put(key, price);
                }
            }
        }

        return cache;
    }

    /**
     * Finds item price using the cache
     */
    private double findItemPrice(ItemSignature itemSignature, Map<String, Double> priceCache) {
        if (priceCache == null) {
            return 0.0;
        }
        String itemKey = createItemKey(itemSignature);
        Double price = priceCache.get(itemKey);
        return price != null ? price : 0.0;
    }

    /**
     * Convenience overload
     */
    private String createItemKey(ItemStack itemStack) {
        if (itemStack == null) return "null";

        return createItemKey(new ItemSignature(itemStack));
    }

    /**
     * Creates a unique key for an item (same logic as SpawnerSellManager)
     * TODO: this feels very wrong ngl
     */
    private String createItemKey(ItemSignature itemSignature) {
        StringBuilder key = new StringBuilder();
        key.append(itemSignature.getMaterial().name());

        // Add enchantments if present
        ItemMeta meta = itemSignature.getUnsafeTemplateRef().getItemMeta(); // Read-only
        if (itemSignature.hasItemMeta() && meta.hasEnchants()) {
            key.append("_enchants:");
            meta.getEnchants().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                    .forEach(entry -> key.append(entry.getKey().getKey()).append(":").append(entry.getValue()).append(","));
        }

        // Add display name if present
        if (itemSignature.hasItemMeta() && meta.hasDisplayName()) {
            key.append("_name:").append(meta.displayName());
        }

        return key.toString();
    }

    /**
     * Adds items to virtual inventory and updates accumulated sell value
     * This is the preferred method to add items to maintain accurate sell value cache
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity
     * @param items Items to add
     */
    public void addItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // CRITICAL: Acquire inventoryLock to ensure VirtualInventory remains source of truth
        inventoryLock.lock();
        try {
            // Consolidate items being added for efficient price lookup
            Map<ItemSignature, Long> itemsToAdd = new java.util.HashMap<>();
            for (ItemStack item : items) {
                if (item == null || item.getAmount() <= 0) continue;
                // Use cached signature to avoid excessive cloning
                ItemSignature sig = VirtualInventory.getSignature(item);
                itemsToAdd.merge(sig, (long) item.getAmount(), (a, b) -> a + b);
            }

            // Add to VirtualInventory (source of truth) - this operation is atomic within the lock
            virtualInventory.addItems(items);

            // Update sell value atomically
            if (!sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                incrementSellValue(itemsToAdd, priceCache);
            }
        } finally {
            inventoryLock.unlock();
        }
    }

    /**
     * Removes items from virtual inventory and updates accumulated sell value
     * THREAD-SAFE: Uses inventoryLock to ensure atomicity
     * @param items Items to remove
     * @return true if items were removed successfully
     */
    public boolean removeItemsAndUpdateSellValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        // CRITICAL: Acquire inventoryLock to ensure VirtualInventory remains source of truth
        inventoryLock.lock();
        try {
            // Remove from VirtualInventory (source of truth) - atomic operation within lock
            boolean removed = virtualInventory.removeItems(items);

            // Update sell value atomically if removal was successful
            if (removed && !sellValueDirty) {
                Map<String, Double> priceCache = createPriceCache();
                decrementSellValue(items, priceCache);
            }

            return removed;
        } finally {
            inventoryLock.unlock();
        }
    }

    public synchronized void storePreGeneratedLoot(List<ItemStack> items, long experience) {
        this.preGeneratedItems = items;
        this.preGeneratedExperience = experience;
    }

    public synchronized List<ItemStack> getAndClearPreGeneratedItems() {
        List<ItemStack> items = preGeneratedItems;
        preGeneratedItems = null;
        return items;
    }

    public synchronized long getAndClearPreGeneratedExperience() {
        long exp = preGeneratedExperience;
        preGeneratedExperience = 0;
        return exp;
    }

    public synchronized boolean hasPreGeneratedLoot() {
        return (preGeneratedItems != null && !preGeneratedItems.isEmpty()) || preGeneratedExperience > 0;
    }

    public synchronized void setPreGenerating(boolean generating) {
        this.isPreGenerating = generating;
    }

    public synchronized boolean isPreGenerating() {
        return isPreGenerating;
    }

    public synchronized void clearPreGeneratedLoot() {
        preGeneratedItems = null;
        preGeneratedExperience = 0;
        isPreGenerating = false;
    }

    /**
     * Checks if this is an item spawner (spawns items instead of entities)
     * @return true if this spawner spawns items
     */
    public boolean isItemSpawner() {
        return entityType == EntityType.ITEM && spawnedItemMaterial != null;
    }
}
