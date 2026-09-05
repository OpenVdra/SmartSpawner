package github.nighter.smartspawner.spawner.model;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.hologram.SpawnerHologram;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SpawnerData {
    @Getter private final SmartSpawner plugin;
    @Getter private String spawnerId;
    @Getter private final Location spawnerLocation;

    // Lock striping: each guards a different slice of state; never held across a Scheduler boundary.
    @Getter private final ReentrantLock inventoryLock = new ReentrantLock();
    @Getter private final ReentrantLock lootGenerationLock = new ReentrantLock();
    @Getter private final ReentrantLock dataLock = new ReentrantLock();

    // CAS sell guard; every virtual-inventory operation checks isSelling() first.
    private final AtomicBoolean selling = new AtomicBoolean(false);
    private final AtomicBoolean storageDirty = new AtomicBoolean(false);
    // Bumped on every inventory mutation; storage views poll it to decide whether to redraw.
    @Getter private final AtomicLong storageVersion = new AtomicLong(0);

    // Per-single-spawner config, scaled by stackSize into the calculated values below.
    @Getter private long baseMaxStoredExp;
    private int baseMaxStorageSlots;
    @Getter @Setter private int baseMinMobs;
    @Getter @Setter private int baseMaxMobs;

    @Getter private long spawnerExp;
    @Getter @Setter private Boolean spawnerActive;
    @Getter @Setter private Integer spawnerRange;
    @Getter private AtomicBoolean spawnerStop;
    @Getter @Setter private Boolean isAtCapacity;
    @Getter @Setter private Long lastSpawnTime;
    @Getter private long spawnDelay;

    @Getter private EntityType entityType;
    @Getter private String configName;
    @Getter private EntityLootConfig lootConfig;
    @Getter private Material spawnedItemMaterial;

    // Calculated from the base values times stackSize.
    @Getter private int maxSpawnerLootSlots;
    @Getter @Setter private long maxStoredExp;
    @Getter @Setter private int minMobs;
    @Getter @Setter private int maxMobs;

    @Getter private int stackSize;
    @Getter @Setter private int maxStackSize;

    @Getter private VirtualInventory virtualInventory;
    @Getter private final Set<Material> filteredItems = new HashSet<>();
    @Getter @Setter private String lastInteractedPlayer;
    @Getter @Setter private Material preferredSortItem;
    @Getter @Setter private long cachedSpawnDelay;

    private SpawnerHologram hologram;
    private volatile Boolean cachedHasNoLoot;

    private final SpawnerSellValue sellValue = new SpawnerSellValue(this);
    private final SpawnerStorageOps storageOps = new SpawnerStorageOps(this, sellValue);
    private final PreGeneratedLoot preGenLoot = new PreGeneratedLoot();

    public SpawnerData(String id, Location location, EntityType type, SmartSpawner plugin) {
        this(id, location, type, defaultMobName(plugin, type), plugin);
    }

    public SpawnerData(String id, Location location, EntityType type, String configName, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = type;
        this.configName = configName == null || configName.isBlank()
                ? defaultMobName(plugin, type) : configName;
        this.spawnedItemMaterial = null;

        initializeDefaults();
        loadConfigurationValues();
        calculateStackBasedValues();
        initializeComponents();
    }

    // Constructor for item spawners
    public SpawnerData(String id, Location location, Material itemMaterial, SmartSpawner plugin) {
        this(id, location, itemMaterial, defaultItemName(plugin, itemMaterial), plugin);
    }

    public SpawnerData(String id, Location location, Material itemMaterial, String configName, SmartSpawner plugin) {
        super();
        this.plugin = plugin;
        this.spawnerId = id;
        this.spawnerLocation = location;
        this.entityType = EntityType.ITEM;
        this.spawnedItemMaterial = itemMaterial;
        this.configName = configName == null || configName.isBlank()
                ? defaultItemName(plugin, itemMaterial) : configName;

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
        this.preferredSortItem = null;
    }

    public void loadConfigurationValues() {
        this.baseMaxStoredExp = plugin.getConfig().getLong("spawner_properties.default.max_stored_exp", 1000L);
        this.baseMaxStorageSlots = plugin.getConfig().getInt("spawner_properties.default.max_storage_slots", 45);
        this.baseMinMobs = plugin.getConfig().getInt("spawner_properties.default.min_mobs", 1);
        this.baseMaxMobs = plugin.getConfig().getInt("spawner_properties.default.max_mobs", 4);
        this.maxStackSize = plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 1000);
        this.spawnDelay = plugin.getTimeFromConfig("spawner_properties.default.delay", "25s");
        this.cachedSpawnDelay = (this.spawnDelay + 20L) * 50L;
        this.spawnerRange = plugin.getConfig().getInt("spawner_properties.default.range", 16);

        if (isItemSpawner() && spawnedItemMaterial != null) {
            var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
    }

    private static String defaultMobName(SmartSpawner plugin, EntityType type) {
        var definition = plugin.getSpawnerSettingsConfig().getDefaultDefinition(type);
        return definition != null ? definition.name() : type.name().toLowerCase(Locale.ROOT) + "_spawner";
    }

    private static String defaultItemName(SmartSpawner plugin, Material material) {
        var definition = plugin.getItemSpawnerSettingsConfig().getDefaultDefinition(material);
        return definition != null ? definition.name() : material.name().toLowerCase(Locale.ROOT) + "_spawner";
    }

    public void recalculateAfterConfigReload() {
        calculateStackBasedValues();
        sellValue.markDirty();
        updateHologramData();
        invalidateMenuCache();
    }

    /** Like {@link #recalculateAfterConfigReload()} but for API-driven changes, keeping the sell value. */
    public void recalculateAfterAPIModification() {
        calculateStackBasedValues();
        updateHologramData();
        invalidateMenuCache();
    }

    private void invalidateMenuCache() {
        if (plugin.getSpawnerMenuUI() != null) {
            plugin.getSpawnerMenuUI().invalidateSpawnerCache(this.spawnerId);
        }
    }

    private void calculateStackBasedValues() {
        this.maxStoredExp = clampToLong(baseMaxStoredExp * stackSize, 0L, Long.MAX_VALUE);
        setMaxSpawnerLootSlots(clampToInt((long) baseMaxStorageSlots * stackSize, 0, Integer.MAX_VALUE));
        this.minMobs = clampToInt((long) baseMinMobs * stackSize, 0, Integer.MAX_VALUE);
        this.maxMobs = clampToInt((long) baseMaxMobs * stackSize, 0, Integer.MAX_VALUE);
        this.spawnerExp = clampToLong(this.spawnerExp, 0L, this.maxStoredExp);
    }

    /**
     * API compatibility: storage capacity used to be expressed in pages (45 slots each). Addons still
     * call these; they convert to and from the slot-based {@link #baseMaxStorageSlots}. Returns the
     * page-equivalent, rounded up so a partial page still counts.
     */
    public int getBaseMaxStoragePages() {
        return Math.max(1, (baseMaxStorageSlots + 44) / 45);
    }

    /** API compatibility: sets storage capacity from a page count (1 page = 45 slots). */
    public void setBaseMaxStoragePages(int baseMaxStoragePages) {
        this.baseMaxStorageSlots = Math.max(0, baseMaxStoragePages) * 45;
    }

    public void setMaxSpawnerLootSlots(int maxSpawnerLootSlots) {
        this.maxSpawnerLootSlots = Math.max(0, maxSpawnerLootSlots);
        if (virtualInventory != null) {
            virtualInventory.setMaxSlots(this.maxSpawnerLootSlots);
        }
    }

    public void setVirtualInventory(VirtualInventory virtualInventory) {
        this.virtualInventory = virtualInventory;
        if (this.virtualInventory != null) {
            this.virtualInventory.setMaxSlots(this.maxSpawnerLootSlots);
        }
    }

    public void setSpawnDelay(long baseSpawnerDelay) {
        this.spawnDelay = baseSpawnerDelay > 0 ? baseSpawnerDelay : 500;
        long ticksWithBuffer = this.spawnDelay > Long.MAX_VALUE - 20L ? Long.MAX_VALUE : this.spawnDelay + 20L;
        this.cachedSpawnDelay = ticksWithBuffer > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticksWithBuffer * 50L;
        if (baseSpawnerDelay <= 0) {
            plugin.getLogger().warning("Invalid spawner delay value. Setting to default: 500 ticks (25s)");
        }
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
        // dataLock then inventoryLock, always in this order to avoid deadlock; lootGenerationLock left free.
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

        // Allow decreasing past a lowered config limit (e.g. on break); only block increases beyond it.
        if (newStackSize > this.maxStackSize && newStackSize > this.stackSize) {
            plugin.getLogger().warning("Stack size " + newStackSize + " exceeds maximum " + this.maxStackSize + ". Ignoring.");
            return;
        }

        this.stackSize = newStackSize;
        calculateStackBasedValues();
        // Reset the spawn timer so breaking and re-stacking can't trigger immediate loot.
        this.lastSpawnTime = System.currentTimeMillis();
        updateHologramData();
        invalidateMenuCache();
    }

    public void setSpawnerExp(long exp) {
        this.spawnerExp = Math.clamp(exp, 0L, maxStoredExp);
        updateHologramData();
        invalidateMenuCache();
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
            hologram.updateData(stackSize, entityType, spawnedItemMaterial, spawnerExp, maxStoredExp,
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
        var definition = plugin.getSpawnerSettingsConfig().getDefaultDefinition(newType);
        this.configName = definition != null ? definition.name() : defaultMobName(plugin, newType);
        this.lootConfig = definition != null ? definition.lootConfig()
                : plugin.getSpawnerSettingsConfig().getLootConfig(newType);
        sellValue.markDirty();
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
        return item.isAvailable() && !filteredItems.contains(item.material());
    }

    public int getEntityExperienceValue() {
        return lootConfig != null ? lootConfig.experience() : 0;
    }

    /** @return true if this spawner has no loot and no experience (e.g. Allay); cached. */
    public boolean hasNoLootOrExperience() {
        if (cachedHasNoLoot != null) {
            return cachedHasNoLoot;
        }
        cachedHasNoLoot = lootConfig == null
                || (lootConfig.experience() == 0 && getValidLootItems().isEmpty());
        return cachedHasNoLoot;
    }

    public void setLootConfig() {
        if (isItemSpawner() && spawnedItemMaterial != null) {
            var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getItemSpawnerSettingsConfig().getLootConfig(spawnedItemMaterial);
        } else {
            var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
            this.lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        }
        sellValue.markDirty();
        this.cachedHasNoLoot = null;
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

    public boolean isSellValueDirty() {
        return sellValue.isDirty();
    }

    /** Marks the accumulated sell value stale, forcing a full recalculation on the next sell. */
    public void markSellValueDirty() {
        sellValue.markDirty();
    }

    public double getAccumulatedSellValue() {
        return sellValue.getAccumulated();
    }

    /** Recomputes the accumulated sell value from the current inventory (on load or when dirty). */
    public void recalculateSellValue() {
        sellValue.recalculate();
    }

    // ============== Storage operations (serialized on inventoryLock, see SpawnerStorageOps) ==============

    public void addItemsAndUpdateSellValue(Map<ItemSignature, Long> items) {
        storageOps.addItems(items);
    }

    /**
     * Dupe-safe take: atomically removes up to {@code desired} of each signature and returns exactly
     * what was removed, so a caller only ever hands the player back what this reports.
     */
    public Map<ItemSignature, Long> takeItems(Map<ItemSignature, Long> desired) {
        return storageOps.take(desired);
    }

    public Map<ItemSignature, Long> takeItemsFromCellRange(int startSlot, int count) {
        return storageOps.takeCellRange(startSlot, count);
    }

    public Map<ItemSignature, Long> takeItemFromCell(int globalSlot, long maxAmount) {
        return storageOps.takeCell(globalSlot, maxAmount);
    }

    public boolean removeItemsAndUpdateSellValue(List<ItemStack> items) {
        return storageOps.remove(items);
    }

    public boolean removeItemsAndUpdateSellValue(Map<ItemSignature, Long> items) {
        return storageOps.remove(items);
    }

    /** @return timestamp (ms) the last storage viewer closed, or 0 if never. */
    public long getStorageLastEmptyAt() {
        return storageOps.getLastEmptyAt();
    }

    /** Records that the last storage viewer just closed, starting the reorder grace window. */
    public void markStorageEmptyNow() {
        storageOps.markEmptyNow();
    }

    /** @return true if the storage display order is currently pinned. */
    public boolean isStorageOrderFrozen() {
        return storageOps.isOrderFrozen();
    }

    public void freezeStorageOrder(boolean resort) {
        storageOps.freezeOrder(resort);
    }

    public void applySortPreference(Material sort) {
        storageOps.applySortPreference(sort);
    }

    public void storePreGeneratedLoot(Map<ItemSignature, Long> items, long experience) {
        preGenLoot.store(items, experience);
    }

    public Map<ItemSignature, Long> getAndClearPreGeneratedItems() {
        return preGenLoot.takeItems();
    }

    public long getAndClearPreGeneratedExperience() {
        return preGenLoot.takeExperience();
    }

    public boolean hasPreGeneratedLoot() {
        return preGenLoot.has();
    }

    public void setPreGenerating(boolean generating) {
        preGenLoot.setGenerating(generating);
    }

    public boolean isPreGenerating() {
        return preGenLoot.isGenerating();
    }

    public void clearPreGeneratedLoot() {
        preGenLoot.clear();
    }

    public boolean isItemSpawner() {
        return entityType == EntityType.ITEM && spawnedItemMaterial != null;
    }
}
