package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import net.kyori.adventure.text.Component;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SpawnerSellConfirmUI {
    private static final int GUI_SIZE = 27;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Cached layout - loaded once for performance
    private GuiLayout cachedLayout;

    public SpawnerSellConfirmUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        loadLayout();
    }

    private void loadLayout() {
        this.cachedLayout = plugin.getGuiLayoutConfig().getCurrentSellConfirmLayout();
    }

    public void reload() {
        loadLayout();
    }

    public enum PreviousGui {
        MAIN_MENU,
        STORAGE
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui, boolean collectExp) {
        if (player == null || spawner == null) {
            return;
        }

        // Check if there are items to sell before opening
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            plugin.getMessageService().sendMessage(player, "spawner_storage_empty");
            return;
        }

        // OPTIMIZATION: Check if sell confirmation should be skipped
        if (plugin.getGuiLayoutConfig().isSkipSellConfirmation()) {
            // Guard against a sell already in progress for this spawner
            if (spawner.isSelling()) {
                return;
            }

            // Collect exp if requested
            if (collectExp) {
                plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
            }

            player.closeInventory();
            plugin.getSpawnerSellManager().sellAllItems(player, spawner, null);
            return;
        }

        // Guard against opening a second sell GUI while a sell is already in progress.
        if (spawner.isSelling()) {
            return;
        }

        // Cache title - no placeholders needed
        String title = languageManager.getGuiTitle("gui_title_sell_confirm", null);
        Inventory gui = Bukkit.createInventory(new SpawnerSellConfirmHolder(spawner, previousGui, collectExp), GUI_SIZE, title);

        populateSellConfirmGui(gui, player, spawner, collectExp);

        player.openInventory(gui);
    }

    private void populateSellConfirmGui(Inventory gui, Player player, SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Create placeholders once and reuse for all buttons
        Map<String, String> placeholders = createPlaceholders(spawner, collectExp);

        // OPTIMIZATION: Use cached layout instead of querying every time
        if (cachedLayout == null) {
            plugin.getLogger().warning("Sell confirm layout not loaded, using empty GUI");
            return;
        }

        // Iterate through all buttons in the layout
        for (GuiButton button : cachedLayout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            ItemStack buttonItem;

            // Check if this is an info button (spawner display)
            if (button.isInfoButton()) {
                buttonItem = createSpawnerInfoButton(player, spawner, placeholders);
            } else {
                // OPTIMIZATION: Use getAnyActionFromButton to check all click types
                String action = getAnyActionFromButton(button);
                if (action == null || action.isEmpty()) {
                    continue;
                }

                switch (action) {
                    case "cancel":
                        buttonItem = createCancelButton(button.getMaterial(), placeholders);
                        break;
                    case "confirm":
                        buttonItem = createConfirmButton(button.getMaterial(), placeholders, collectExp);
                        break;
                    case "none":
                        // Display-only button (spawner info) - fallback for old format
                        buttonItem = createSpawnerInfoButton(player, spawner, placeholders);
                        break;
                    default:
                        plugin.getLogger().warning("Unknown action in sell confirm GUI: " + action);
                        continue;
                }
            }

            gui.setItem(button.getSlot(), buttonItem);
        }
    }

    private ItemStack createCancelButton(Material material, Map<String, String> placeholders) {
        String name = languageManager.getGuiItemName("button_sell_cancel.name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_sell_cancel.lore", placeholders);
        return createButton(material, name, lore);
    }

    private ItemStack createConfirmButton(Material material, Map<String, String> placeholders, boolean collectExp) {
        // Use different button key based on whether exp is collected
        String buttonKey = collectExp ? "button_sell_confirm_with_exp" : "button_sell_confirm";
        String name = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);
        return createButton(material, name, lore);
    }

    private ItemStack createSpawnerInfoButton(Player player, SpawnerData spawner, Map<String, String> placeholders) {
        // Build loot item components for {loot_items} placeholder
        Map<ItemSignature, Long> storedItems = spawner.getVirtualInventory().getConsolidatedItems();
        List<Component> lootComponents = buildSellInfoLootComponents(spawner, storedItems);

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            // Set display name
            meta.setDisplayName(languageManager.getGuiItemName("button_sell_info.name", placeholders));

            // Get and set lore with {loot_items} support
            List<Component> lore = languageManager.buildGuiLoreAsComponents(
                    "button_sell_info.lore", placeholders, lootComponents, "button_sell_info.loot_items_empty");
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack spawnerItem;

        // OPTIMIZATION: Get cached spawner type from placeholders
        if (placeholders.containsKey("spawnedItem")) {
            spawnerItem = SpawnerMobHeadTexture.getItemSpawnerHead(
                Material.valueOf(placeholders.get("spawnedItem")), player, metaModifier);
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(
                EntityType.valueOf(placeholders.get("entityType")),
                player, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(spawnerItem);
        }

        return spawnerItem;
    }

    private List<Component> buildSellInfoLootComponents(SpawnerData spawner, Map<ItemSignature, Long> storedItems) {
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getMaterial();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        EntityType entityType = spawner.getEntityType();
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
                        "button_sell_info.loot_items", material, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(languageManager.buildTranslatableGuiLootLine(
                        "button_sell_info.loot_items", material, formattedAmount, ""));
            }
        }
        return components;
    }

    private Map<String, String> createPlaceholders(SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Calculate initial capacity to avoid HashMap resizing
        Map<String, String> placeholders = new HashMap<>(12);

        // OPTIMIZATION: Get entity name once and cache
        String entityName;
        boolean isItemSpawner = spawner.isItemSpawner();

        if (isItemSpawner) {
            Material spawnedItem = spawner.getSpawnedItemMaterial();
            entityName = languageManager.getVanillaItemName(spawnedItem);
            placeholders.put("spawnedItem", spawnedItem.name());
        } else {
            org.bukkit.entity.EntityType entityType = spawner.getEntityType();
            entityName = languageManager.getFormattedMobName(entityType);
            placeholders.put("entityType", entityType.name());
        }

        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));

        // OPTIMIZATION: Check sell value dirty only once
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }

        // OPTIMIZATION: Get all values in single pass
        double totalSellPrice = spawner.getAccumulatedSellValue();
        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxItems = spawner.getMaxSpawnerLootSlots();
        double percentStorageDecimal = maxItems > 0 ? ((double) currentItems / maxItems) * 100 : 0;
        long currentExp = spawner.getSpawnerExp();

        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        placeholders.put("current_items", Integer.toString(currentItems));
        placeholders.put("max_items", Integer.toString(maxItems));
        placeholders.put("percent_storage_decimal", String.format("%.1f", percentStorageDecimal));
        placeholders.put("percent_storage_rounded", String.valueOf((int) Math.round(percentStorageDecimal)));
        placeholders.put("current_exp", languageManager.formatNumber(currentExp));

        return placeholders;
    }

    private ItemStack createButton(Material material, String name, String[] lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
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
