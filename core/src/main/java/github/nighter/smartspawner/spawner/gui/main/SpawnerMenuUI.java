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
import github.nighter.smartspawner.language.section.GuiLanguageSection;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    // Cache frequently used formatting strings and pattern lookups
    private static final String LOOT_ITEM_FORMAT_KEY = "main.storage.loot_items";
    private static final String EMPTY_LOOT_MESSAGE_KEY = "main.storage.loot_items_empty";
    private static final String TIME_PLACEHOLDER = "{time}";

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Format strings - initialized in constructor to avoid repeated lookups

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

        GuiLayout layout = plugin.getGuiLayoutConfig().getMainLayout(spawner, player);
        Inventory menu = createMenu(player, spawner, layout);

        // Populate menu items from the layout. A button's item is chosen by its identity
        // (the info_button flag, or a recognized action), not by requiring an action: an
        // enabled button with a valid key always appears, even with no action at all.
        ItemStack[] items = new ItemStack[INVENTORY_SIZE];

        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            // Check condition if present
            if (button.hasCondition() && !evaluateButtonCondition(button, player)) {
                continue;
            }

            ItemStack item;
            if (button.isInfoButton()) {
                // The spawner info button is display-oriented; it does not need an action to show.
                item = createSpawnerInfoItem(player, spawner, button);
            } else {
                // Check all click types, not just the default one.
                String action = getAnyActionFromButton(button);
                if (action == null || action.isEmpty()) {
                    action = "none";
                }
                switch (action) {
                    case "open_storage":
                        item = createLootStorageItem(player, spawner, button);
                        break;
                    case "collect_exp":
                        item = createExpItem(player, spawner, button);
                        break;
                    case "sell_and_exp":
                        item = createSpawnerInfoItem(player, spawner, button);
                        break;
                    case "none":
                        // Display-only button: show it as configured with no behavior.
                        item = createStaticItem(button);
                        break;
                    default:
                        // Unknown action: keep the button visible rather than hiding it.
                        item = createStaticItem(button);
                        break;
                }
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
            plugin.getGuiButtonInteractionService().playOpenSound(player);
        }

        // Force timer update inactive for GUI if applicable
        if (plugin.getSpawnerGuiViewManager().isTimerPlaceholdersEnabled() && spawner.getSpawnerStop().get()){
            plugin.getSpawnerGuiViewManager().forceTimerUpdateInactive(player, spawner);
        }
    }

    private Inventory createMenu(Player player, SpawnerData spawner, GuiLayout layout) {
        // Get entity name with caching - for item spawners, use item name
        String entityName = spawner.getDisplayName();
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        // Use string builder for efficient placeholder creation
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        GuiLanguageSection gui = languageManager.gui(player);
        Component title = spawner.getStackSize() > 1
                ? gui.title("main.title_stacked", placeholders)
                : gui.title("main.title", placeholders);

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner, layout), INVENTORY_SIZE, title);
    }

    public ItemStack createLootStorageItem(Player player, SpawnerData spawner, GuiButton button) {
        GuiLanguageSection gui = languageManager.gui(player);
        // Generate cache key based on spawner state and button config
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|storage|" + currentItems + "|" + maxSlots + "|" + virtualInventory.hashCode() + "|" + buttonConfig
                + "|" + languageManager.localeKey(player);
        
        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        // Smart placeholder detection: First, get the raw name and lore templates
        String nameTemplate = gui.raw("main.storage.name");
        List<String> loreTemplate = gui.rawList("main.storage.lore");
        
        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "max_slots", "current_items", "percent_storage_rounded", "total_sell_price", "loot_items"
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
        if (usedPlaceholders.contains("total_sell_price")) {
            if (spawner.isSellValueDirty()) {
                spawner.recalculateSellValue();
            }
            placeholders.put("total_sell_price", languageManager.formatNumber(spawner.getAccumulatedSellValue()));
        }
        final List<Component> finalLootComponents = usedPlaceholders.contains("loot_items") 
                ? buildLootItemComponents(gui, spawner, virtualInventory.getConsolidatedItems())
                : Collections.emptyList();

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.displayName(gui.name("main.storage.name", placeholders));
            List<Component> lore = gui.loreComponents(
                    "main.storage.lore", placeholders, finalLootComponents, EMPTY_LOOT_MESSAGE_KEY);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        };

        ItemStack chestItem;
        if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            chestItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            Material mat = (button != null) ? button.getMaterial() : cachedStorageMaterial;
            chestItem = new ItemStack(mat);
            chestItem.editMeta(metaModifier);
        }

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (chestItem.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(chestItem);
        }

        // Cache the result
        itemCache.put(cacheKey, chestItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return chestItem;
    }

    public ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner, GuiButton button) {
        GuiLanguageSection gui = languageManager.gui(player);
        // Get important data upfront
        EntityType entityType = spawner.getEntityType();
        int stackSize = spawner.getStackSize();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();

        // Create cache key including all relevant state and button config
        boolean hasShopPermission = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");
        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|info|" + currentItems + "|" + maxSlots + "|" + currentExp + "|" + maxExp + "|" + hasShopPermission + "|" + buttonConfig
                + "|" + languageManager.localeKey(player);

        // Check cache first. The cached item keeps the {time} placeholder unresolved (the
        // countdown is never baked in), so resolve it against the live timer on every return.
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            ItemStack result = cachedItem.clone();
            applyTimerPlaceholder(result, spawner, player);
            return result;
        }

        // Smart placeholder detection: First, get the raw name and lore templates
        String nameTemplate = gui.raw("main.info.name");
        String loreKey = hasShopPermission ? "main.info.lore" : "main.info.lore_no_shop";
        List<String> loreTemplate = gui.rawList(loreKey);

        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "entity", "ᴇɴᴛɪᴛʏ", "entity_small", "stack_size", "range", "delay", "min_mobs", "max_mobs",
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
        boolean smallEntity = usedPlaceholders.contains("ᴇɴᴛɪᴛʏ") || usedPlaceholders.contains("entity_small");
        if (usedPlaceholders.contains("entity") || smallEntity) {
            String entityName = spawner.getDisplayName();
            if (usedPlaceholders.contains("entity")) {
                placeholders.put("entity", entityName);
            }
            if (smallEntity) {
                placeholders.put("entity_small", languageManager.getSmallCaps(entityName));
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

        // NOTE: {time} is deliberately left unresolved here so it is not baked into the cached
        // item. The live countdown is a per-tick value; baking it would freeze a stale time in the
        // cache (and pollute the placeholder cache). It is resolved via applyTimerPlaceholder() on
        // every return instead, so the item always shows the current countdown.

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            meta.displayName(gui.name("main.info.name", placeholders));
            meta.lore(gui.lore(loreKey, placeholders));
        };

        ItemStack spawnerItem;

        // Check if this is an item spawner and use appropriate head
        if (spawner.isItemSpawner()) {
            // For item spawners, use the head configured in spawner_items.yml
            spawnerItem = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
        } else if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            // Use custom texture from GUI layout if provided
            spawnerItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else if (button != null && button.getMaterial() == Material.PLAYER_HEAD) {
            // Fallback to this spawner's own head (from spawner_mobs.yml)
            spawnerItem = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
        } else if (button != null) {
            // Use the configured material
            spawnerItem = new ItemStack(button.getMaterial());
            spawnerItem.editMeta(metaModifier);
        } else {
            // Fallback to default behavior
            spawnerItem = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) ItemTooltipUtil.hideTooltip(spawnerItem);

        // Cache the result while {time} is still an unresolved placeholder, then resolve it on the
        // returned copy so the cache never holds a frozen countdown value.
        itemCache.put(cacheKey, spawnerItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        applyTimerPlaceholder(spawnerItem, spawner, player);

        return spawnerItem;
    }

    /**
     * Resolves the {time} placeholder in an already-built spawner info item against the current
     * countdown. Does nothing (and never computes the timer) when the item uses no {time}
     * placeholder, so layouts without a timer pay no cost. This is intentionally kept outside the
     * item and placeholder caches, because the countdown changes every second.
     */
    private void applyTimerPlaceholder(ItemStack item, SpawnerData spawner, Player player) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Component name = meta.hasDisplayName() ? meta.displayName() : null;
        boolean nameHasTimer = name != null && hasTimePlaceholder(name);
        List<Component> lore = meta.hasLore() ? meta.lore() : null;
        boolean loreHasTimer = false;
        if (lore != null) {
            for (Component line : lore) {
                if (hasTimePlaceholder(line)) {
                    loreHasTimer = true;
                    break;
                }
            }
        }

        if (!nameHasTimer && !loreHasTimer) {
            return;
        }

        Component timerValue = plugin.getSpawnerGuiViewManager().calculateTimerDisplay(spawner, player);
        TextReplacementConfig replaceTime = TextReplacementConfig.builder()
                .matchLiteral(TIME_PLACEHOLDER)
                .replacement(timerValue)
                .build();

        if (nameHasTimer) {
            meta.displayName(name.replaceText(replaceTime));
        }
        if (loreHasTimer) {
            List<Component> updatedLore = new ArrayList<>(lore.size());
            for (Component line : lore) {
                updatedLore.add(line.replaceText(replaceTime));
            }
            meta.lore(updatedLore);
        }
        item.setItemMeta(meta);
    }

    private static boolean hasTimePlaceholder(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text).contains(TIME_PLACEHOLDER);
    }

    public ItemStack createExpItem(Player player, SpawnerData spawner, GuiButton button) {
        GuiLanguageSection gui = languageManager.gui(player);
        // Get important data upfront
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        int percentExp = calculatePercentage(currentExp, maxExp);

        // Create cache key including button config
        String buttonConfig = (button != null) ? (button.getMaterial().name() + "_" + button.getCustomTexture()) : "default";
        String cacheKey = spawner.getSpawnerId() + "|exp|" + currentExp + "|" + maxExp + "|" + buttonConfig
                + "|" + languageManager.localeKey(player);

        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

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

        Consumer<ItemMeta> metaModifier = meta -> {
            meta.displayName(gui.name("main.exp.name", placeholders));
            meta.lore(gui.lore("main.exp.lore", placeholders));
        };

        ItemStack expItem;
        if (button != null && button.getMaterial() == Material.PLAYER_HEAD && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            expItem = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), metaModifier);
        } else {
            Material mat = (button != null) ? button.getMaterial() : cachedExpMaterial;
            expItem = new ItemStack(mat);
            expItem.editMeta(metaModifier);
        }

        // Hide tooltip for BUNDLE material (prevents showing bundle contents)
        if (expItem.getType() == Material.BUNDLE) {
            ItemTooltipUtil.hideTooltip(expItem);
        }

        // Cache the result
        itemCache.put(cacheKey, expItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return expItem;
    }

    /**
     * Builds a display-only item for a button that has no behavior (no action, or an
     * unrecognized one). It renders the configured material or custom head so the button
     * stays visible in the menu based on its layout key alone.
     */
    private ItemStack createStaticItem(GuiButton button) {
        ItemStack item;
        if (button.getMaterial() == Material.PLAYER_HEAD
                && button.getCustomTexture() != null && !button.getCustomTexture().trim().isEmpty()) {
            item = SpawnerMobHeadTexture.getCustomHeadFromTexture(button.getCustomTexture(), null);
        } else {
            item = new ItemStack(button.getMaterial());
        }
        if (item.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(item);
        }
        return item;
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    private List<Component> buildLootItemComponents(GuiLanguageSection gui, SpawnerData spawner,
                                                    Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
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
                Material material = lootItem.material();
                long amount = materialAmountMap.getOrDefault(material, 0L);
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.chance()) + "%";
                components.add(gui.translatableLootLine(LOOT_ITEM_FORMAT_KEY,
                        languageManager.getItemDisplayName(lootItem.template()), formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(gui.translatableLootLine(LOOT_ITEM_FORMAT_KEY,
                        languageManager.getItemDisplayName(entry.getKey().getTemplate()), formattedAmount, ""));
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
