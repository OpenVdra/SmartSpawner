package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import net.kyori.adventure.text.Component;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
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

    public SpawnerSellConfirmUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    public void reload() {
        // No-op: layout is resolved per-context via GuiLayoutConfig
    }

    public enum PreviousGui {
        MAIN_MENU,
        STORAGE
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui, boolean collectExp) {
        openSellConfirmGui(player, spawner, previousGui, collectExp, null, "click");
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui,
                                   boolean collectExp, GuiButton sourceButton) {
        openSellConfirmGui(player, spawner, previousGui, collectExp, sourceButton, "click");
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui,
                                   boolean collectExp, GuiButton sourceButton,
                                   String sourceClickType) {
        if (player == null || spawner == null) {
            return;
        }

        // Check if there are items to sell before opening
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            plugin.getMessageService().sendMessage(player, "storage.empty");
            if (sourceButton != null) {
                plugin.getGuiButtonInteractionService().playFailSound(
                        player, sourceButton, sourceClickType);
            }
            return;
        }

        // OPTIMIZATION: Check if sell confirmation should be skipped
        if (plugin.getGuiLayoutConfig().isSkipSellConfirmation()) {
            // Guard against a sell already in progress for this spawner
            if (spawner.isSelling()) {
                return;
            }

            // Collect exp silently so we can send a single combined sell+exp message
            long expCollected = 0;
            long expMending = 0;
            if (collectExp) {
                long[] expData = plugin.getSpawnerMenuAction().collectExpSilently(player, spawner);
                expCollected = expData[0];
                expMending = expData[1];
            }

            player.closeInventory();
            Runnable onComplete = sourceButton == null ? null : () -> {
                if (spawner.getVirtualInventory().getUsedSlots() == 0) {
                    plugin.getGuiButtonInteractionService().playSuccessSound(
                            player, sourceButton, sourceClickType);
                } else {
                    plugin.getGuiButtonInteractionService().playFailSound(
                            player, sourceButton, sourceClickType);
                }
            };
            plugin.getSpawnerSellManager().sellAllItems(
                    player, spawner, onComplete, expCollected, expMending);
            return;
        }

        // Guard against opening a second sell GUI while a sell is already in progress.
        if (spawner.isSelling()) {
            return;
        }

        if (sourceButton != null) {
            plugin.getGuiButtonInteractionService().playNavigateSound(
                    player, sourceButton, sourceClickType);
        }

        Component title = languageManager.gui(player).title("sell_confirm.title");
        GuiLayout layout = plugin.getGuiLayoutConfig().getSellConfirmLayout(spawner, player);
        Inventory gui = Bukkit.createInventory(
                new SpawnerSellConfirmHolder(spawner, previousGui, collectExp, layout), GUI_SIZE, title);

        populateSellConfirmGui(gui, player, spawner, collectExp, layout);

        player.openInventory(gui);
    }

    private void populateSellConfirmGui(Inventory gui, Player player, SpawnerData spawner,
                                        boolean collectExp, GuiLayout layout) {
        // OPTIMIZATION: Create placeholders once and reuse for all buttons
        Map<String, String> placeholders = createPlaceholders(spawner, collectExp);
        GuiLanguageSection texts = languageManager.gui(player);

        if (layout == null) {
            plugin.getLogger().warning("Sell confirm layout not loaded, using empty GUI");
            return;
        }

        // Iterate through all buttons in the layout
        for (GuiButton button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }
            if (button.hasCondition() && !evaluateButtonCondition(button)) {
                continue;
            }

            ItemStack buttonItem;

            // Check if this is an info button (spawner display)
            if (button.isInfoButton()) {
                buttonItem = createSpawnerInfoButton(texts, spawner, placeholders);
            } else {
                // OPTIMIZATION: Use getAnyActionFromButton to check all click types
                String action = getAnyActionFromButton(button);
                if (action == null || action.isEmpty()) {
                    continue;
                }

                switch (action) {
                    case "cancel":
                        buttonItem = createButton(texts, button.getMaterial(), "sell_confirm.cancel", placeholders);
                        break;
                    case "confirm":
                        // Different button when exp is collected too
                        buttonItem = createButton(texts, button.getMaterial(),
                                collectExp ? "sell_confirm.confirm_with_exp" : "sell_confirm.confirm", placeholders);
                        break;
                    case "none":
                        // Display-only button (spawner info) - fallback for old format
                        buttonItem = createSpawnerInfoButton(texts, spawner, placeholders);
                        break;
                    default:
                        plugin.getLogger().warning("Unknown action in sell confirm GUI: " + action);
                        continue;
                }
            }

            gui.setItem(button.getSlot(), buttonItem);
        }
    }

    private boolean evaluateButtonCondition(GuiButton button) {
        return switch (button.getCondition()) {
            case "sell_integration", "shop_integration" -> plugin.hasSellIntegration();
            case "no_sell_integration", "no_shop_integration" -> !plugin.hasSellIntegration();
            default -> true;
        };
    }

    private ItemStack createSpawnerInfoButton(GuiLanguageSection texts, SpawnerData spawner, Map<String, String> placeholders) {
        // Build loot item components for {loot_items} placeholder
        Map<ItemSignature, Long> storedItems = spawner.getVirtualInventory().getConsolidatedItems();
        List<Component> lootComponents = buildSellInfoLootComponents(texts, spawner, storedItems);

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            meta.displayName(texts.name("sell_confirm.summary.name", placeholders));

            // Get and set lore with {loot_items} support
            List<Component> lore = texts.loreComponents(
                    "sell_confirm.summary.lore", placeholders, lootComponents, "sell_confirm.summary.loot_items_empty");
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack spawnerItem = SpawnerMobHeadTexture.getSpawnerHead(spawner, metaModifier);

        if (spawnerItem.getType() == Material.SPAWNER) {
            ItemTooltipUtil.hideTooltip(spawnerItem);
        }

        return spawnerItem;
    }

    private List<Component> buildSellInfoLootComponents(GuiLanguageSection texts, SpawnerData spawner,
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
                components.add(texts.translatableLootLine(
                        "sell_confirm.summary.loot_items", material, formattedAmount, chance));
            }
        } else {
            List<Map.Entry<ItemSignature, Long>> sortedItems = new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            for (Map.Entry<ItemSignature, Long> entry : sortedItems) {
                Material material = entry.getKey().getMaterial();
                long amount = entry.getValue();
                String formattedAmount = languageManager.formatNumber(amount);
                components.add(texts.translatableLootLine(
                        "sell_confirm.summary.loot_items", material, formattedAmount, ""));
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
            entityName = spawner.getDisplayName();
            placeholders.put("spawnedItem", spawnedItem.name());
        } else {
            org.bukkit.entity.EntityType entityType = spawner.getEntityType();
            entityName = spawner.getDisplayName();
            placeholders.put("entityType", entityType.name());
        }

        placeholders.put("entity", entityName);
        placeholders.put("entity_small", languageManager.getSmallCaps(entityName));

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

    private ItemStack createButton(GuiLanguageSection texts, Material material, String textKey,
                                   Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(texts.name(textKey + ".name", placeholders));
            List<Component> lore = texts.lore(textKey + ".lore", placeholders);
            if (!lore.isEmpty()) {
                meta.lore(lore);
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
