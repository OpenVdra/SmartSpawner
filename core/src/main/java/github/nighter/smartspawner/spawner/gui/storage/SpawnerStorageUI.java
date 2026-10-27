package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.format.TextFormatter;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SpawnerStorageUI {
    private static final String STORAGE_TITLE = "storage.title";
    private static final String SINGLE_STORAGE_TITLE = "storage.title_single";

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
    private final Map<String, ItemStack> sortButtonCache;

    // Cache expiry time reduced for more responsive updates
    private static final int MAX_CACHE_SIZE = 100;
    // Sort button key cardinality is (spawner types x their loot items), ~200+ on a full vanilla
    // server, so it gets a larger cap than the page-based caches to avoid thrashing. Each entry is
    // a few KB, so even at this cap the retained memory stays around 1 MB.
    private static final int MAX_SORT_CACHE_SIZE = 300;
    
    // Cleanup task to remove stale entries from caches
    private Task cleanupTask;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new ConcurrentHashMap<>(16);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);
        this.sortButtonCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    public void reload() {
        // Reload layout configuration
        this.layoutConfig = plugin.getGuiLayoutConfig();

        // Clear caches to force reloading of buttons
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        sortButtonCache.clear();
        staticButtons.clear();

        // Reinitialize static buttons
        initializeStaticButtons();
    }

    /** Builds the server-language static buttons up front; other languages are built on first use. */
    private void initializeStaticButtons() {
        GuiLayout layout = layoutConfig.getCurrentStorageLayout();

        // OPTIMIZATION: Iterate through all buttons and create static buttons by action
        for (GuiButton button : layout.getAllButtons().values()) {
            String action = getAnyActionFromButton(button);
            if (action == null) continue;
            String textKey = staticButtonTextKey(action);
            if (textKey != null) {
                getStaticButton(null, textKey, button);
            }
            // Note: Sort and sell buttons are created dynamically to show current values
        }
    }

    /** The gui.yml button for an action whose item never changes, or null. */
    private static String staticButtonTextKey(String action) {
        return switch (action) {
            case "return_main" -> "storage.back";
            case "take_all" -> "storage.take_all";
            case "drop_page" -> "storage.drop_page";
            case "open_filter" -> "storage.filter";
            default -> null;
        };
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
     * @param viewer The player the title is for
     * @param spawner The spawner data
     * @param page Current page number
     * @param totalPages Total number of pages
     * @return Formatted title with page information
     */
    public Component storageTitle(Player viewer, SpawnerData spawner, int page, int totalPages) {
        GuiLanguageSection gui = languageManager.gui(viewer);
        String titleKey = spawner.getStackSize() == 1 && gui.contains(SINGLE_STORAGE_TITLE)
                ? SINGLE_STORAGE_TITLE
                : STORAGE_TITLE;
        String titleFormat = gui.raw(titleKey);

        // Build base placeholders (always present)
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("current_page", String.valueOf(page));
        placeholders.put("total_pages", String.valueOf(totalPages));

        // OPTIMIZATION: Only compute entity placeholders if they exist in the title format
        boolean smallEntity = titleFormat.contains("{ᴇɴᴛɪᴛʏ}") || titleFormat.contains("{entity_small}");
        if (titleFormat.contains("{entity}") || smallEntity) {
            String entityName = spawner.getDisplayName();

            if (titleFormat.contains("{entity}")) {
                placeholders.put("entity", entityName);
            }
            if (smallEntity) {
                placeholders.put("entity_small", languageManager.getSmallCaps(entityName));
            }
        }

        // OPTIMIZATION: Only compute amount if it exists in the title format
        if (titleFormat.contains("{amount}")) {
            placeholders.put("amount", String.valueOf(spawner.getStackSize()));
        }

        return gui.title(titleKey, placeholders);
    }

    /** {@link #storageTitle} for {@code InventoryView.setTitle}, which only takes a string. */
    public String getStorageTitle(Player viewer, SpawnerData spawner, int page, int totalPages) {
        return TextFormatter.toLegacy(storageTitle(viewer, spawner, page, totalPages));
    }

    public Inventory createStorageInventory(Player player, SpawnerData spawner, int page, int totalPages) {
        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        GuiLayout layout = layoutConfig.getStorageLayout(spawner, player);

        // Create inventory with title including page info using placeholder-based format
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages, layout),
                INVENTORY_SIZE,
                storageTitle(player, spawner, page, totalPages)
        );

        // Populate the inventory
        updateDisplay(player, pageInv, spawner, page, totalPages);
        return pageInv;
    }

    /** Repaints for the inventory's viewer; the buttons are in that player's language. */
    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        Player viewer = null;
        for (HumanEntity human : inventory.getViewers()) {
            if (human instanceof Player player) {
                viewer = player;
                break;
            }
        }
        updateDisplay(viewer, inventory, spawner, page, totalPages);
    }

    private void updateDisplay(Player viewer, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        if (!spawner.getInventoryLock().tryLock()) {
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
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        assert holder != null;
        GuiLayout layout = holder.getLayout();
        if (layout != null) {
            slotsToEmpty.addAll(layout.getUsedSlots());
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons based on layout
        addNavigationButtons(viewer, updates, spawner, page, totalPages, layout);

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
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty, SpawnerData spawner, int page) {
        try {
            // Read only the requested page instead of materializing the full logical inventory.
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            Int2ObjectMap<ItemStack> displayItems = virtualInv.getDisplayPage(page, StoragePageHolder.MAX_ITEMS_PER_PAGE);

            if (displayItems.isEmpty()) {
                return;
            }

            for (Int2ObjectMap.Entry<ItemStack> entry : displayItems.int2ObjectEntrySet()) {
                int displaySlot = entry.getIntKey();
                updates.put(displaySlot, entry.getValue());
                slotsToEmpty.remove(displaySlot);
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    private void addNavigationButtons(Player viewer, Map<Integer, ItemStack> updates, SpawnerData spawner, int page,
                                      int totalPages, GuiLayout layout) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }
        GuiLanguageSection gui = languageManager.gui(viewer);
        String localeKey = languageManager.localeKey(viewer);

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
                updates.put(button.getSlot(), createStorageSpawnerInfoButton(gui, spawner, button));
                continue;
            }

            String action = getAnyActionFromButton(button);
            if (action == null) continue;

            ItemStack item = null;

            switch (action) {
                case "previous_page":
                    if (page > 1) {
                        String cacheKey = "prev-" + (page - 1) + "-" + button.getCustomTexture() + "-" + localeKey;
                        item = navigationButtonCache.computeIfAbsent(
                                cacheKey, k -> createNavigationButton(gui, "previous", page - 1, button));
                    }
                    break;
                case "next_page":
                    if (page < totalPages) {
                        String cacheKey = "next-" + (page + 1) + "-" + button.getCustomTexture() + "-" + localeKey;
                        item = navigationButtonCache.computeIfAbsent(
                                cacheKey, k -> createNavigationButton(gui, "next", page + 1, button));
                    }
                    break;
                case "take_all":
                case "drop_page":
                case "open_filter":
                case "return_main":
                    item = getStaticButton(viewer, staticButtonTextKey(action), button);
                    break;
                case "sort_items":
                    item = createSortButton(gui, localeKey, spawner, button);
                    break;
                case "sell_all":
                    item = createPriceButton(gui, "storage.sell", spawner, button);
                    break;
                case "sell_and_exp":
                    item = createPriceButton(gui, "storage.sell_and_exp", spawner, button);
                    break;
                case "collect_exp":
                    item = createCollectExpButton(gui, spawner, button);
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

    private ItemStack createButtonWithCustomTexture(GuiButton button, Consumer<ItemMeta> metaModifier) {
        ItemStack item;
        if (button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            item = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            item = new ItemStack(button.getMaterial());
            item.editMeta(metaModifier);
        }

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (item.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideBundleTooltip(item);
        }

        return item;
    }

    /** A button whose item never changes, cached per button look and viewer language. */
    private ItemStack getStaticButton(Player viewer, String textKey, GuiButton button) {
        String cacheKey = textKey + "|" + button.getMaterial() + "|" + button.getCustomTexture()
                + "|" + languageManager.localeKey(viewer);
        GuiLanguageSection gui = languageManager.gui(viewer);
        return staticButtons.computeIfAbsent(cacheKey, ignored -> createButtonWithCustomTexture(button, meta -> {
            meta.displayName(gui.name(textKey + ".name"));
            meta.lore(gui.lore(textKey + ".lore"));
        })).clone();
    }

    private ItemStack createNavigationButton(GuiLanguageSection gui, String type, int targetPage, GuiButton button) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target_page", String.valueOf(targetPage));

        String buttonKey = type.equals("previous") ? "storage.previous_page" : "storage.next_page";
        Component buttonName = gui.name(buttonKey + ".name", placeholders);
        List<Component> buttonLore = gui.lore(buttonKey + ".lore", placeholders);

        return createButtonWithCustomTexture(button, meta -> {
            meta.displayName(buttonName);
            if (!buttonLore.isEmpty()) {
                meta.lore(buttonLore);
            }
        });
    }

    /** The sell buttons, which show the current total sell price. */
    private ItemStack createPriceButton(GuiLanguageSection gui, String textKey, SpawnerData spawner, GuiButton button) {
        Map<String, String> placeholders = new HashMap<>();
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }
        placeholders.put("total_sell_price", languageManager.formatNumber(spawner.getAccumulatedSellValue()));

        return createButtonWithCustomTexture(button, meta -> {
            meta.displayName(gui.name(textKey + ".name", placeholders));
            meta.lore(gui.lore(textKey + ".lore", placeholders));
        });
    }

    private ItemStack createCollectExpButton(GuiLanguageSection gui, SpawnerData spawner, GuiButton button) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current_exp", languageManager.formatNumber(spawner.getSpawnerExp()));

        return createButtonWithCustomTexture(button, meta -> {
            meta.displayName(gui.name("storage.collect_exp.name", placeholders));
            meta.lore(gui.lore("storage.collect_exp.lore", placeholders));
        });
    }

    private ItemStack createSortButton(GuiLanguageSection gui, String localeKey, SpawnerData spawner, GuiButton button) {
        Material currentSort = spawner.getPreferredSortItem();

        // Cache key = loot identity (the available_items list) + selected sort + button look.
        // These only change on click or reload, so the cached ItemStack is reused across redraws.
        String lootKey = spawner.isItemSpawner()
                ? "item:" + spawner.getSpawnedItemMaterial()
                : "mob:" + spawner.getEntityType();
        String cacheKey = lootKey
                + "|" + (currentSort == null ? "none" : currentSort.name())
                + "|" + button.getMaterial()
                + "|" + button.getCustomTexture()
                + "|" + localeKey;

        return sortButtonCache.computeIfAbsent(
                cacheKey, ignored -> buildSortButton(gui, spawner, button, currentSort)).clone();
    }

    private ItemStack buildSortButton(GuiLanguageSection gui, SpawnerData spawner, GuiButton button, Material currentSort) {
        // One lore line per loot item, each from the selected or unselected template.
        List<Component> availableItems = new ArrayList<>();
        if (spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            var sortedLoot = spawner.getLootConfig().getAllItems().stream()
                .sorted(Comparator.comparing(item -> item.material().name()))
                .toList();

            for (var lootItem : sortedLoot) {
                String itemName = languageManager.getVanillaItemName(lootItem.material());
                String format = currentSort == lootItem.material() ? "storage.sort.selected_item" : "storage.sort.unselected_item";
                availableItems.add(gui.name(format, Map.of("item_name", itemName)));
            }
        }

        if (availableItems.isEmpty()) {
            availableItems.add(gui.name("storage.sort.no_item"));
        }

        return createButtonWithCustomTexture(button, meta -> {
            meta.displayName(gui.name("storage.sort.name"));
            meta.lore(gui.lore("storage.sort.lore", Map.of(), Map.of("available_items", availableItems)));
        });
    }

    private ItemStack createStorageSpawnerInfoButton(GuiLanguageSection gui, SpawnerData spawner, GuiButton button) {
        Map<ItemSignature, Long> storedItems = spawner.getVirtualInventory().getConsolidatedItems();
        List<Component> lootComponents = buildStorageInfoLootComponents(gui, spawner, storedItems);

        Map<String, String> placeholders = new HashMap<>();
        String entityName = spawner.getDisplayName();
        placeholders.put("entity", entityName);
        placeholders.put("entity_small", languageManager.getSmallCaps(entityName));
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
            meta.displayName(gui.name("storage.info.name", placeholders));
            List<Component> lore = gui.loreComponents(
                    "storage.info.lore", placeholders, lootComponents,
                    "storage.info.loot_items_empty");
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack item;
        if (spawner.isItemSpawner()) {
            item = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
        } else if (button.getMaterial() == Material.PLAYER_HEAD) {
            String customTexture = button.getCustomTexture();
            if (customTexture != null && !customTexture.trim().isEmpty()) {
                item = SpawnerMobHeadTexture.getCustomHeadFromTexture(customTexture, metaModifier);
            } else {
                item = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
            }
        } else {
            item = new ItemStack(button.getMaterial());
            item.editMeta(metaModifier);
        }

        if (item.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(item);
        }

        return item;
    }

    private List<Component> buildStorageInfoLootComponents(GuiLanguageSection gui, SpawnerData spawner,
            Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material mat = entry.getKey().getMaterial();
            materialAmountMap.merge(mat, entry.getValue(), Long::sum);
        }

        EntityLootConfig lootConfig = spawner.getLootConfig();
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
                components.add(gui.translatableLootLine(
                        "storage.info.loot_items", mat, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material mat = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(gui.translatableLootLine(
                        "storage.info.loot_items", mat, formattedAmount, ""));
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

        // LRU-like cleanup for sort buttons
        if (sortButtonCache.size() > MAX_SORT_CACHE_SIZE) {
            int toRemove = sortButtonCache.size() - (MAX_SORT_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(sortButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                sortButtonCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();
        sortButtonCache.clear();

        // Cancel scheduled tasks
        cancelTasks();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}
