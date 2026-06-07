package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import net.kyori.adventure.text.Component;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.api.events.SpawnerOpenGUIEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    // Cache frequently used formatting strings and pattern lookups
    private static final String LOOT_ITEM_FORMAT_KEY = "spawner_storage_item.loot_items";
    private static final String EMPTY_LOOT_MESSAGE_KEY = "spawner_storage_item.loot_items_empty";

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Format strings - initialized in constructor to avoid repeated lookups
    private String lootItemFormat;
    private String emptyLootMessage;

    // Cached materials from layout config (for performance)
    private Material cachedStorageMaterial = Material.CHEST;
    private Material cachedExpMaterial = Material.EXPERIENCE_BOTTLE;

    // Cache for GUI items - cleared when spawner data changes
    // Using ConcurrentHashMap for thread-safety with Folia's async scheduler
    private final Map<String, ItemStack> itemCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME_MS = 30000; // 30 seconds

    public SpawnerMenuUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        loadConfig();
    }

    public void loadConfig() {
        clearCache();
        this.lootItemFormat = languageManager.getGuiItemName(LOOT_ITEM_FORMAT_KEY, EMPTY_PLACEHOLDERS);
        this.emptyLootMessage = languageManager.getGuiItemName(EMPTY_LOOT_MESSAGE_KEY, EMPTY_PLACEHOLDERS);

        // OPTIMIZATION: Cache materials from layout config for performance
        // Find buttons by their action instead of name
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();

        Material storageMaterial = Material.CHEST; // default
        Material expMaterial = Material.EXPERIENCE_BOTTLE; // default

        for (GuiButton button : layout.getAllButtons().values()) {
            String action = button.getDefaultAction();
            if (action == null) continue;

            if ("open_storage".equals(action)) {
                storageMaterial = button.getMaterial();
            } else if ("collect_exp".equals(action)) {
                expMaterial = button.getMaterial();
            }
        }

        this.cachedStorageMaterial = storageMaterial;
        this.cachedExpMaterial = expMaterial;
    }

    public void clearCache() {
        itemCache.clear();
        cacheTimestamps.clear();
    }

    public void invalidateSpawnerCache(String spawnerId) {
        itemCache.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
        cacheTimestamps.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
    }

    private boolean isCacheEntryExpired(String cacheKey) {
        Long timestamp = cacheTimestamps.get(cacheKey);
        return timestamp == null || System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME_MS;
    }

    public void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
        if(SpawnerOpenGUIEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerOpenGUIEvent openEvent = new SpawnerOpenGUIEvent(
                    player,
                    spawner.getSpawnerLocation(),
                    spawner.getEntityType(),
                    spawner.getStackSize(),
                    refresh
            );
            Bukkit.getPluginManager().callEvent(openEvent);

            if (openEvent.isCancelled()) {
                return;
            }
        }

        Inventory menu = createMenu(spawner);
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();

        // OPTIMIZATION: Populate menu items based on layout configuration
        // Iterate through ALL buttons in layout and create items based on their actions
        ItemStack[] items = new ItemStack[INVENTORY_SIZE];
        
        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            // Check condition if present
            if (button.hasCondition() && !evaluateButtonCondition(button, player)) {
                continue;
            }

            // OPTIMIZATION: Get action - check all action types not just default
            // A button might have left_click/right_click but no click
            String action = getAnyActionFromButton(button);
            if (action == null || action.isEmpty()) {
                continue;
            }

            ItemStack item = null;
            switch (action) {
                case "open_storage":
                    item = createLootStorageItem(spawner);
                    break;
                case "open_stacker":
                case "sell_and_exp":
                case "none":
                    // Spawner info button
                    item = createSpawnerInfoItem(player, spawner);
                    break;
                case "collect_exp":
                    item = createExpItem(spawner);
                    break;
                default:
                    plugin.getLogger().warning("Unknown action in main GUI: " + action);
                    continue;
            }

            if (item != null) {
                items[button.getSlot()] = item;
            }
        }

        // Set all items at once instead of one by one
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                menu.setItem(i, items[i]);
            }
        }

        // Open inventory and play sound if not refreshing
        player.openInventory(menu);

        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }

        // Force timer update inactive for GUI if applicable
        if (plugin.getSpawnerGuiViewManager().isTimerPlaceholdersEnabled() && spawner.getSpawnerStop().get()){
            plugin.getSpawnerGuiViewManager().forceTimerUpdateInactive(player, spawner);
        }
    }

    private Inventory createMenu(SpawnerData spawner) {
        // Get entity name with caching - for item spawners, use item name
        String entityName;
        if (spawner.isItemSpawner()) {
            entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
        } else {
            entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        }
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        // Use string builder for efficient placeholder creation
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner), INVENTORY_SIZE, title);
    }

    public ItemStack createLootStorageItem(SpawnerData spawner) {
        // Generate cache key based on spawner state
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        String cacheKey = spawner.getSpawnerId() + "|storage|" + currentItems + "|" + maxSlots + "|" + virtualInventory.hashCode();
        
        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        // Use cached material for performance (no layout lookup needed)
        ItemStack chestItem = new ItemStack(cachedStorageMaterial);
        ItemMeta chestMeta = chestItem.getItemMeta();
        if (chestMeta == null) return chestItem;

        // Smart placeholder detection: First, get the raw name and lore templates
        String nameTemplate = languageManager.getGuiItemName("spawner_storage_item.name", EMPTY_PLACEHOLDERS);
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList("spawner_storage_item.lore", EMPTY_PLACEHOLDERS);
        
        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "max_slots", "current_items", "percent_storage_rounded", "loot_items"
        );
        
        // Detect which placeholders are actually used
        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));
        
        // Build only the placeholders that are actually used
        Map<String, String> placeholders = new HashMap<>();
        
        if (usedPlaceholders.contains("max_slots")) {
            placeholders.put("max_slots", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("percent_storage_rounded")) {
            int percentStorage = calculatePercentage(currentItems, maxSlots);
            placeholders.put("percent_storage_rounded", String.valueOf(percentStorage));
        }
        List<Component> lootComponents = Collections.emptyList();
        if (usedPlaceholders.contains("loot_items")) {
            Map<ItemSignature, Long> storedItems = virtualInventory.getConsolidatedItems();
            lootComponents = buildLootItemComponents(spawner.getEntityType(), storedItems);
        }

        // Set display name
        chestMeta.setDisplayName(languageManager.getGuiItemName("spawner_storage_item.name", placeholders));

        // Build lore as Adventure Components (italic=false, item names use client-side translation)
        List<Component> lore = languageManager.buildGuiLoreAsComponents(
                "spawner_storage_item.lore", placeholders, lootComponents, EMPTY_LOOT_MESSAGE_KEY);
        if (!lore.isEmpty()) {
            chestMeta.lore(lore);
        }
        chestItem.setItemMeta(chestMeta);

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (cachedStorageMaterial == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(chestItem);
        }

        // Cache the result
        itemCache.put(cacheKey, chestItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return chestItem;
    }

    private String buildLootItemsText(EntityType entityType, Map<ItemSignature, Long> storedItems) {
        // Create material-to-amount map for quick lookups
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        // Get possible loot items
        EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        List<LootItem> possibleLootItems = lootConfig != null
                ? lootConfig.getAllItems()
                : Collections.emptyList();

        // Return early for empty cases
        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return emptyLootMessage;
        }

        // Use StringBuilder for efficient string concatenation
        StringBuilder builder = new StringBuilder(Math.max(possibleLootItems.size(), storedItems.size()) * 40);

        if (!possibleLootItems.isEmpty()) {
            // Sort items by name for consistent display
            possibleLootItems.sort(Comparator.comparing(item -> languageManager.getVanillaItemName(item.material())));

            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.material();
                long amount = materialAmountMap.getOrDefault(material, 0L);

                String materialName = languageManager.getVanillaItemName(material);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";

                // Format the line with minimal string operations
                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", chance);

                builder.append(line).append('\n');
            }
        } else if (!storedItems.isEmpty()) {
            // Sort items by name
            List<Map.Entry<ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));

            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();

                String materialName = languageManager.getVanillaItemName(material);
                String formattedAmount = languageManager.formatNumber(amount);

                // Format with minimal replacements
                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", "");

                builder.append(line).append('\n');
            }
        }

        // Remove trailing newline if it exists
        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == '\n') {
            builder.setLength(length - 1);
        }

        return builder.toString();
    }

    public ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
        // Get layout configuration first
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();

        // OPTIMIZATION: Find spawner info button by info_button flag first
        GuiButton spawnerInfoButton = null;
        for (GuiButton button : layout.getAllButtons().values()) {
            // Check info_button flag first (most reliable)
            if (button.isInfoButton()) {
                spawnerInfoButton = button;
                break;
            }

            // Fallback: check by action for backward compatibility
            String action = getAnyActionFromButton(button);
            if (action != null && (action.equals("open_stacker") || action.equals("sell_and_exp") || action.equals("none"))) {
                // Check if button condition matches current state
                if (!button.hasCondition() || evaluateButtonCondition(button, player)) {
                    spawnerInfoButton = button;
                    break;
                }
            }
        }

        // Get important data upfront
        EntityType entityType = spawner.getEntityType();
        int stackSize = spawner.getStackSize();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();

        // Create cache key including all relevant state
        boolean hasShopPermission = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");

        // Not in cache, create the ItemStack

        // Smart placeholder detection: First, get the raw name and lore templates
        String nameTemplate = languageManager.getGuiItemName("spawner_info_item.name", EMPTY_PLACEHOLDERS);
        String loreKey = hasShopPermission ? "spawner_info_item.lore" : "spawner_info_item.lore_no_shop";
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList(loreKey, EMPTY_PLACEHOLDERS);

        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "entity", "ᴇɴᴛɪᴛʏ", "stack_size", "range", "delay", "min_mobs", "max_mobs",
            "current_items", "max_items", "percent_storage_decimal", "percent_storage_rounded",
            "current_exp", "max_exp", "raw_current_exp", "raw_max_exp", "percent_exp_decimal", "percent_exp_rounded",
            "total_sell_price", "time"
        );

        // Detect which placeholders are actually used
        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));

        // Prepare only the placeholders that are actually used
        Map<String, String> placeholders = new HashMap<>();

        // Entity information
        if (usedPlaceholders.contains("entity") || usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
            String entityName;
            // For item spawners, use the item name instead of "Item Spawner"
            if (spawner.isItemSpawner()) {
                entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
            } else {
                entityName = languageManager.getFormattedMobName(entityType);
            }
            if (usedPlaceholders.contains("entity")) {
                placeholders.put("entity", entityName);
            }
            if (usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
                placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
            }
        }

        // Stack information
        if (usedPlaceholders.contains("stack_size")) {
            placeholders.put("stack_size", String.valueOf(stackSize));
        }

        // Spawner settings
        if (usedPlaceholders.contains("range")) {
            placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        }
        if (usedPlaceholders.contains("delay")) {
            long delaySeconds = spawner.getSpawnDelay() / TICKS_PER_SECOND;
            placeholders.put("delay", String.valueOf(delaySeconds));
        }
        if (usedPlaceholders.contains("min_mobs")) {
            placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        }
        if (usedPlaceholders.contains("max_mobs")) {
            placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));
        }

        // Storage information
        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("max_items")) {
            placeholders.put("max_items", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("percent_storage_decimal") || usedPlaceholders.contains("percent_storage_rounded")) {
            double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
            if (usedPlaceholders.contains("percent_storage_decimal")) {
                String formattedPercentStorage = String.format("%.1f", percentStorageDecimal);
                placeholders.put("percent_storage_decimal", formattedPercentStorage);
            }
            if (usedPlaceholders.contains("percent_storage_rounded")) {
                int percentStorageRounded = (int) Math.round(percentStorageDecimal);
                placeholders.put("percent_storage_rounded", String.valueOf(percentStorageRounded));
            }
        }

        // Experience information
        if (usedPlaceholders.contains("current_exp")) {
            placeholders.put("current_exp", languageManager.formatNumber(currentExp));
        }
        if (usedPlaceholders.contains("max_exp")) {
            placeholders.put("max_exp", languageManager.formatNumber(maxExp));
        }
        if (usedPlaceholders.contains("raw_current_exp")) {
            placeholders.put("raw_current_exp", String.valueOf(currentExp));
        }
        if (usedPlaceholders.contains("raw_max_exp")) {
            placeholders.put("raw_max_exp", String.valueOf(maxExp));
        }
        if (usedPlaceholders.contains("percent_exp_decimal") || usedPlaceholders.contains("percent_exp_rounded")) {
            double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
            if (usedPlaceholders.contains("percent_exp_decimal")) {
                String formattedPercentExp = String.format("%.1f", percentExpDecimal);
                placeholders.put("percent_exp_decimal", formattedPercentExp);
            }
            if (usedPlaceholders.contains("percent_exp_rounded")) {
                int percentExpRounded = (int) Math.round(percentExpDecimal);
                placeholders.put("percent_exp_rounded", String.valueOf(percentExpRounded));
            }
        }

        // Total sell price information
        if (usedPlaceholders.contains("total_sell_price")) {
            // Always recalculate if dirty to ensure immediate display (0s delay)
            if (spawner.isSellValueDirty()) {
                spawner.recalculateSellValue();
            }
            double totalSellPrice = spawner.getAccumulatedSellValue();
            placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        }

        // Calculate and add timer value
        if (usedPlaceholders.contains("time")) {
            String timerValue = plugin.getSpawnerGuiViewManager().calculateTimerDisplay(spawner, player);
            placeholders.put("time", timerValue);
        }

        ItemStack spawnerItem;

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            // Set display name with the specified placeholders
            meta.setDisplayName(languageManager.getGuiItemName("spawner_info_item.name", placeholders));

            // Get and set lore with placeholders
            List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders(loreKey, placeholders);
            meta.setLore(lore);
        };

        // Check if this is an item spawner and use appropriate head
        if (spawner.isItemSpawner()) {
            // For item spawners, use the item material as the head
            spawnerItem = SpawnerMobHeadTexture.getItemSpawnerHead(spawner.getSpawnedItemMaterial(), player, metaModifier);
        } else if (spawnerInfoButton != null && spawnerInfoButton.getMaterial() == Material.PLAYER_HEAD) {
            // Use custom head texture for MOB_HEAD material
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player, metaModifier);
        } else if (spawnerInfoButton != null) {
            // Use the configured material
            spawnerItem = new ItemStack(spawnerInfoButton.getMaterial());
            spawnerItem.editMeta(metaModifier);
        } else {
            // Fallback to default behavior
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) ItemTooltipUtil.hideTooltip(spawnerItem);
        return spawnerItem;
    }

    public ItemStack createExpItem(SpawnerData spawner) {
        // Get important data upfront
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        int percentExp = calculatePercentage(currentExp, maxExp);

        // Create cache key for this specific spawner's exp state
        String cacheKey = spawner.getSpawnerId() + "|exp|" + currentExp + "|" + maxExp;

        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        // Use cached material for performance (no layout lookup needed)
        ItemStack expItem = new ItemStack(cachedExpMaterial);
        ItemMeta expMeta = expItem.getItemMeta();
        if (expMeta == null) return expItem;

        // Format numbers once for display
        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);

        // Prepare all placeholders
        Map<String, String> placeholders = new HashMap<>(5); // Preallocate with expected capacity
        placeholders.put("current_exp", formattedExp);
        placeholders.put("raw_current_exp", String.valueOf(currentExp));
        placeholders.put("max_exp", formattedMaxExp);
        placeholders.put("percent_exp_rounded", String.valueOf(percentExp));
        placeholders.put("u_max_exp", String.valueOf(maxExp));

        // Set name and lore
        expMeta.setDisplayName(languageManager.getGuiItemName("exp_info_item.name", placeholders));
        List<String> loreExp = languageManager.getGuiItemLoreAsList("exp_info_item.lore", placeholders);
        expMeta.setLore(loreExp);

        expItem.setItemMeta(expMeta);

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (cachedExpMaterial == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(expItem);
        }

        // Cache the result
        itemCache.put(cacheKey, expItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return expItem;
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    private List<Component> buildLootItemComponents(EntityType entityType, Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        List<LootItem> possibleLootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();

        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> components = new ArrayList<>();
        if (!possibleLootItems.isEmpty()) {
            possibleLootItems.sort(Comparator.comparing(item -> item.material().name()));
            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.material();
                long amount = materialAmountMap.getOrDefault(material, 0L);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";
                components.add(languageManager.buildTranslatableGuiLootLine(
                        LOOT_ITEM_FORMAT_KEY, material, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(languageManager.buildTranslatableGuiLootLine(
                        LOOT_ITEM_FORMAT_KEY, material, formattedAmount, ""));
            }
        }
        return components;
    }

    /**
     * Detects which placeholders are actually used in the given text
     * @param text The text to scan for placeholders
     * @param availablePlaceholders Set of all available placeholder keys
     * @return Set of placeholder keys that are actually used in the text
     */
    private Set<String> detectUsedPlaceholders(String text, Set<String> availablePlaceholders) {
        Set<String> usedPlaceholders = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return usedPlaceholders;
        }
        
        for (String placeholder : availablePlaceholders) {
            if (text.contains("{" + placeholder + "}")) {
                usedPlaceholders.add(placeholder);
            }
        }
        return usedPlaceholders;
    }

    /**
     * Detects which placeholders are actually used in the given list of strings
     * @param textList The list of strings to scan for placeholders
     * @param availablePlaceholders Set of all available placeholder keys
     * @return Set of placeholder keys that are actually used in any of the texts
     */
    private Set<String> detectUsedPlaceholders(List<String> textList, Set<String> availablePlaceholders) {
        Set<String> usedPlaceholders = new HashSet<>();
        if (textList == null || textList.isEmpty()) {
            return usedPlaceholders;
        }
        
        for (String text : textList) {
            for (String placeholder : availablePlaceholders) {
                if (text.contains("{" + placeholder + "}")) {
                    usedPlaceholders.add(placeholder);
                }
            }
        }
        return usedPlaceholders;
    }

    /**
     * Evaluate button condition based on player/server state
     * OPTIMIZATION: Centralized condition evaluation
     */
    private boolean evaluateButtonCondition(GuiButton button, org.bukkit.entity.Player player) {
        String condition = button.getCondition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        switch (condition) {
            case "sell_integration":
                return plugin.hasSellIntegration();
            case "no_sell_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown button condition: " + condition);
                return true;
        }
    }

    /**
     * Get any action from button - checks click, left_click, right_click
     * OPTIMIZATION: Return first found action for item creation
     */
    private String getAnyActionFromButton(GuiButton button) {
        // Check in priority order: click -> left_click -> right_click
        String action = button.getDefaultAction(); // checks "click" first
        if (action != null && !action.isEmpty()) {
            return action;
        }

        // Check left_click
        action = button.getAction("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        // Check right_click
        action = button.getAction("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }
}
