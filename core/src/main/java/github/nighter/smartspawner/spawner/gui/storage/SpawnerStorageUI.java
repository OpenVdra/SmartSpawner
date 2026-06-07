package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.Scheduler.Task;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SpawnerStorageUI {
    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    @Getter
    private GuiLayoutConfig layoutConfig;

    // Precomputed buttons to avoid repeated creation
    private final Map<String, ItemStack> staticButtons;

    // Lightweight caches with better eviction strategies
    private final Map<String, ItemStack> navigationButtonCache;
    private final Map<String, ItemStack> pageIndicatorCache;

    // Cache expiry time reduced for more responsive updates
    private static final int MAX_CACHE_SIZE = 100;
    
    // Cache for title format to avoid repeated language lookups
    private String cachedStorageTitleFormat = null;

    // Cleanup task to remove stale entries from caches
    private Task cleanupTask;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new HashMap<>(8);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    public void reload() {
        // Reload layout configuration
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Clear caches to force reloading of buttons
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        staticButtons.clear();
        cachedStorageTitleFormat = null;

        // Reinitialize static buttons
        initializeStaticButtons();
    }

    private void initializeStaticButtons() {
        GuiLayout layout = layoutConfig.getCurrentStorageLayout();

        // OPTIMIZATION: Iterate through all buttons and create static buttons by action
        for (GuiButton button : layout.getAllButtons().values()) {
            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            switch (action) {
                case "return_main":
                    staticButtons.put("return", createButton(
                            button.getMaterial(),
                            languageManager.getGuiItemName("return_button.name"),
                            languageManager.getGuiItemLoreAsList("return_button.lore")
                    ));
                    break;
                case "take_all":
                    staticButtons.put("takeAll", createButton(
                            button.getMaterial(),
                            languageManager.getGuiItemName("take_all_button.name"),
                            languageManager.getGuiItemLoreAsList("take_all_button.lore")
                    ));
                    break;
                case "sort_items":
                    // Sort button is created dynamically (material stored for later)
                    break;
                case "drop_page":
                    staticButtons.put("dropPage", createButton(
                            button.getMaterial(),
                            languageManager.getGuiItemName("drop_page_button.name"),
                            languageManager.getGuiItemLoreAsList("drop_page_button.lore")
                    ));
                    break;
                case "open_filter":
                    staticButtons.put("itemFilter", createButton(
                            button.getMaterial(),
                            languageManager.getGuiItemName("item_filter_button.name"),
                            languageManager.getGuiItemLoreAsList("item_filter_button.lore")
                    ));
                    break;
                // Note: Sell buttons are created dynamically to show current total sell price
            }
        }
    }

    /**
     * Get any action from button - checks click, left_click, right_click
     * OPTIMIZATION: Return first found action
     */
    private String getAnyActionFromButton(GuiButton button) {
        // Check click type actions in priority order
        Map<String, String> actions = button.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        // Check in priority: click -> left_click -> right_click
        String action = actions.get("click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }

    /**
     * Gets the formatted title for storage GUI with page information.
     * Uses cached title format pattern for performance.
     * Optimized to only compute entity placeholders if they're used in the title.
     *
     * @param spawner The spawner data
     * @param page Current page number
     * @param totalPages Total number of pages
     * @return Formatted title with page information
     */
    private String getStorageTitle(SpawnerData spawner, int page, int totalPages) {
        // Cache the title format pattern (not the filled title)
        if (cachedStorageTitleFormat == null) {
            cachedStorageTitleFormat = languageManager.getGuiTitle("gui_title_storage");
        }
        
        // Build base placeholders (always present)
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("current_page", String.valueOf(page));
        placeholders.put("total_pages", String.valueOf(totalPages));

        // OPTIMIZATION: Only compute entity placeholders if they exist in the title format
        if (cachedStorageTitleFormat.contains("{entity}") || cachedStorageTitleFormat.contains("{ᴇɴᴛɪᴛʏ}")) {
            String entityName;
            if (spawner.isItemSpawner()) {
                entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
            } else {
                entityName = languageManager.getFormattedMobName(spawner.getEntityType());
            }

            if (cachedStorageTitleFormat.contains("{entity}")) {
                placeholders.put("entity", entityName);
            }
            if (cachedStorageTitleFormat.contains("{ᴇɴᴛɪᴛʏ}")) {
                placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
            }
        }

        // OPTIMIZATION: Only compute amount if it exists in the title format
        if (cachedStorageTitleFormat.contains("{amount}")) {
            placeholders.put("amount", String.valueOf(spawner.getStackSize()));
        }

        return languageManager.getGuiTitle("gui_title_storage", placeholders);
    }

    public Inventory createStorageInventory(SpawnerData spawner, int page, int totalPages) {
        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        // Create inventory with title including page info using placeholder-based format
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                getStorageTitle(spawner, page, totalPages)
        );

        // Populate the inventory
        updateDisplay(pageInv, spawner, page, totalPages);
        return pageInv;
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        if (!spawner.getInventoryLock().tryLock()) {
            if (plugin.isDebugMode()) {
                plugin.debug("Skipping GUI update - inventory operation in progress for spawner " + spawner.getSpawnerId());
            }
            return;
        }

        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Track both changes and slots that need to be emptied
        Map<Integer, ItemStack> updates = new HashMap<>();
        Set<Integer> slotsToEmpty = new HashSet<>();

        // Clear storage area slots first
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            slotsToEmpty.add(i);
        }

        // Also mark all button slots for potential clearing (fixes visual bug where buttons remain when they shouldn't)
        GuiLayout layout = layoutConfig.getCurrentLayout();
        if (layout != null) {
            slotsToEmpty.addAll(layout.getUsedSlots());
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons based on layout
        addNavigationButtons(updates, spawner, page, totalPages);

        // Apply all updates in a batch
        for (int slot : slotsToEmpty) {
            if (!updates.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        // Update hologram if enabled
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            Scheduler.runLocationTask(spawner.getSpawnerLocation(), spawner::updateHologramData);
        }

        // Check if we need to update total pages
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        assert holder != null;
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty,
                              SpawnerData spawner, int page) {
        try {
            // Get display items directly from VirtualInventory (source of truth)
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();

            if (displayItems.isEmpty()) {
                return;
            }

            // Calculate start index for current page
            int startIndex = (page - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

            // Add items for this page
            for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
                int globalIndex = entry.getKey();

                // Check if item belongs on this page
                if (globalIndex >= startIndex && globalIndex < startIndex + StoragePageHolder.MAX_ITEMS_PER_PAGE) {
                    int displaySlot = globalIndex - startIndex;
                    updates.put(displaySlot, entry.getValue());
                    slotsToEmpty.remove(displaySlot);
                }
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page, int totalPages) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        GuiLayout layout = layoutConfig.getCurrentStorageLayout();

        // OPTIMIZATION: Iterate through all buttons and add items based on action
        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            // Check condition if present
            if (button.hasCondition() && !evaluateButtonCondition(button)) {
                continue;
            }

            // Handle info_button (spawner mob head with loot info)
            if (button.isInfoButton()) {
                updates.put(button.getSlot(), createStorageSpawnerInfoButton(spawner, button.getMaterial()));
                continue;
            }

            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            ItemStack item = null;

            switch (action) {
                case "previous_page":
                    if (page > 1) {
                        String cacheKey = "prev-" + (page - 1);
                        item = navigationButtonCache.computeIfAbsent(
                                cacheKey, k -> createNavigationButton("previous", page - 1, button.getMaterial()));
                    }
                    break;
                case "next_page":
                    if (page < totalPages) {
                        String cacheKey = "next-" + (page + 1);
                        item = navigationButtonCache.computeIfAbsent(
                                cacheKey, k -> createNavigationButton("next", page + 1, button.getMaterial()));
                    }
                    break;
                case "take_all":
                    item = staticButtons.get("takeAll");
                    break;
                case "sort_items":
                    item = createSortButton(spawner, button.getMaterial());
                    break;
                case "drop_page":
                    item = staticButtons.get("dropPage");
                    break;
                case "open_filter":
                    item = staticButtons.get("itemFilter");
                    break;
                case "return_main":
                    item = staticButtons.get("return");
                    break;
                case "sell_all":
                    item = createSellButton(spawner, button.getMaterial());
                    break;
                case "sell_and_exp":
                    item = createSellAndExpButton(spawner, button.getMaterial());
                    break;
                case "collect_exp":
                    item = createCollectExpButton(spawner, button.getMaterial());
                    break;
            }

            if (item != null) {
                updates.put(button.getSlot(), item);
            }
        }
    }

    /**
     * Evaluate button condition based on server state
     * OPTIMIZATION: Centralized condition evaluation for storage GUI
     */
    private boolean evaluateButtonCondition(GuiButton button) {
        String condition = button.getCondition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        switch (condition) {
            case "shop_integration":
                return plugin.hasSellIntegration();
            case "no_shop_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown button condition: " + condition);
                return true;
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (material == Material.BUNDLE) {
            ItemTooltipUtil.hideBundleTooltip(item);
        }

        return item;
    }

    private ItemStack createNavigationButton(String type, int targetPage, Material material) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target_page", String.valueOf(targetPage));

        String buttonName;
        String buttonKey;

        if (type.equals("previous")) {
            buttonKey = "navigation_button_previous";
        } else {
            buttonKey = "navigation_button_next";
        }

        buttonName = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] buttonLore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);

        return createButton(material, buttonName, Arrays.asList(buttonLore));
    }

    private ItemStack createSellButton(SpawnerData spawner, Material material) {
        // Create placeholders for total sell price
        Map<String, String> placeholders = new HashMap<>();
        // Check if sell value needs recalculation before displaying
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }
        double totalSellPrice = spawner.getAccumulatedSellValue();
        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        
        String name = languageManager.getGuiItemName("sell_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("sell_button.lore");
        return createButton(material, name, lore);
    }

    private ItemStack createSellAndExpButton(SpawnerData spawner, Material material) {
        // Create placeholders for total sell price
        Map<String, String> placeholders = new HashMap<>();
        // Check if sell value needs recalculation before displaying
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }
        double totalSellPrice = spawner.getAccumulatedSellValue();
        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));

        String name = languageManager.getGuiItemName("sell_and_exp_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("sell_and_exp_button.lore");
        return createButton(material, name, lore);
    }

    private ItemStack createCollectExpButton(SpawnerData spawner, Material material) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current_exp", languageManager.formatNumber(spawner.getSpawnerExp()));

        String name = languageManager.getGuiItemName("collect_exp_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("collect_exp_button.lore");
        return createButton(material, name, lore);
    }

    private ItemStack createSortButton(SpawnerData spawner, Material material) {
        Map<String, String> placeholders = new HashMap<>();

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Get format strings from configuration
        String selectedItemFormat = languageManager.getGuiItemName("sort_items_button.selected_item");
        String unselectedItemFormat = languageManager.getGuiItemName("sort_items_button.unselected_item");
        String noneText = languageManager.getGuiItemName("sort_items_button.no_item");

        // Get available items from spawner drops
        StringBuilder availableItems = new StringBuilder();
        if (spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            boolean first = true;
            var sortedLoot = spawner.getLootConfig().getAllItems().stream()
                .sorted(Comparator.comparing(item -> item.material().name()))
                .toList();

            for (var lootItem : sortedLoot) {
                if (!first) availableItems.append("\n");
                String itemName = languageManager.getVanillaItemName(lootItem.material());
                String format = currentSort == lootItem.material() ? selectedItemFormat : unselectedItemFormat;
                
                // Replace {item_name} placeholder in format string
                String formattedItem = format.replace("{item_name}", itemName);
                availableItems.append(formattedItem);
                first = false;
            }
        }

        if (availableItems.isEmpty()) {
            availableItems.append(noneText);
        }

        placeholders.put("available_items", availableItems.toString());

        String name = languageManager.getGuiItemName("sort_items_button.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders("sort_items_button.lore", placeholders);
        return createButton(material, name, lore);
    }

    private ItemStack createStorageSpawnerInfoButton(SpawnerData spawner, Material material) {
        Map<ItemSignature, Long> storedItems = spawner.getVirtualInventory().getConsolidatedItems();
        List<Component> lootComponents = buildStorageInfoLootComponents(spawner, storedItems);

        Map<String, String> placeholders = new HashMap<>();
        String entityName;
        if (spawner.isItemSpawner()) {
            entityName = languageManager.getVanillaItemName(spawner.getSpawnedItemMaterial());
        } else {
            entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        }
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));

        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
        placeholders.put("percent_storage_decimal", String.format("%.1f", percentStorageDecimal));
        placeholders.put("percent_storage_rounded", String.valueOf((int) Math.round(percentStorageDecimal)));

        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
        placeholders.put("percent_exp_decimal", String.format("%.1f", percentExpDecimal));
        placeholders.put("percent_exp_rounded", String.valueOf((int) Math.round(percentExpDecimal)));

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.setDisplayName(languageManager.getGuiItemName("storage_spawner_info_button.name", placeholders));
            List<Component> lore = languageManager.buildGuiLoreAsComponents(
                    "storage_spawner_info_button.lore", placeholders, lootComponents,
                    "storage_spawner_info_button.loot_items_empty");
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack item;
        if (spawner.isItemSpawner()) {
            item = SpawnerMobHeadTexture.getItemSpawnerHead(spawner.getSpawnedItemMaterial(), metaModifier);
        } else {
            item = SpawnerMobHeadTexture.getCustomHead(spawner.getEntityType(), metaModifier);
        }

        if (item.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(item);
        }

        return item;
    }

    private List<Component> buildStorageInfoLootComponents(SpawnerData spawner,
            Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material mat = entry.getKey().getMaterial();
            materialAmountMap.merge(mat, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig;
        if (spawner.isItemSpawner()) {
            lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(spawner.getSpawnedItemMaterial());
        } else {
            lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(spawner.getEntityType());
        }
        List<LootItem> possibleLootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();

        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> components = new ArrayList<>();
        if (!possibleLootItems.isEmpty()) {
            possibleLootItems.sort(Comparator.comparing(item -> item.material().name()));
            for (LootItem lootItem : possibleLootItems) {
                Material mat = lootItem.material();
                long amount = materialAmountMap.getOrDefault(mat, 0L);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";
                components.add(languageManager.buildTranslatableGuiLootLine(
                        "storage_spawner_info_button.loot_items", mat, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material mat = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(languageManager.buildTranslatableGuiLootLine(
                        "storage_spawner_info_button.loot_items", mat, formattedAmount, ""));
            }
        }
        return components;
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runTaskTimer(this::cleanupCaches, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    public void cancelTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void cleanupCaches() {
        // LRU-like cleanup for navigation buttons
        if (navigationButtonCache.size() > MAX_CACHE_SIZE) {
            int toRemove = navigationButtonCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(navigationButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                navigationButtonCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for page indicators
        if (pageIndicatorCache.size() > MAX_CACHE_SIZE) {
            int toRemove = pageIndicatorCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(pageIndicatorCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                pageIndicatorCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        cachedStorageTitleFormat = null;

        // Cancel scheduled tasks
        cancelTasks();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}
