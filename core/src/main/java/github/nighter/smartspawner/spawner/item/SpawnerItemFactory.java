package github.nighter.smartspawner.spawner.item;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SpawnerItemFactory {

    private static final long CACHE_EXPIRY_TIME_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int MAX_CACHE_SIZE = 100;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private static NamespacedKey VANILLA_SPAWNER_KEY;
    private final Map<EntityType, ItemStack> spawnerItemCache = new HashMap<>();
    private final Map<EntityType, Long> cacheTimestamps = new HashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();

    public SpawnerItemFactory(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
    }

    public void reload() {
        clearAllCaches();
    }

    public void clearAllCaches() {
        spawnerItemCache.clear();
        cacheTimestamps.clear();
        lastCacheCleanup = System.currentTimeMillis();
    }

    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup < TimeUnit.MINUTES.toMillis(1)) {
            return;
        }
        lastCacheCleanup = currentTime;
        Iterator<Map.Entry<EntityType, Long>> iterator = cacheTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityType, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_EXPIRY_TIME_MS) {
                EntityType type = entry.getKey();
                spawnerItemCache.remove(type);
                iterator.remove();
            }
        }
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType) {
        return createSmartSpawnerItem(entityType, 1);
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType, int amount) {
        cleanupCacheIfNeeded();
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(entityType);
            if (cachedItem != null) {
                return cachedItem.clone();
            }
        }

        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String entityTypeNameSmallCaps = languageManager.getSmallCaps(entityTypeName);
            EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", entityTypeNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.experience() : 0));
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.material().name()));
            String nameKey = itemKey("smart_spawner", entityType.name(), "name");
            String lootItemsKey = itemKey("smart_spawner", entityType.name(), "loot_items");
            String emptyLootKey = itemKey("smart_spawner", entityType.name(), "loot_items_empty");
            String loreKey = itemKey("smart_spawner", entityType.name(), "lore");
            // Build translatable loot lines – each player sees item names in their own client language
            List<Component> lootComponents = new ArrayList<>(sortedLootItems.size());
            for (LootItem item : sortedLootItems) {
                String amountRange = item.minAmount() == item.maxAmount() ?
                        String.valueOf(item.minAmount()) :
                        item.minAmount() + "-" + item.maxAmount();
                String chance = String.format("%.1f", item.chance());
                lootComponents.add(languageManager.buildTranslatableLootLine(
                        lootItemsKey, item.material(), amountRange, chance));
            }
            String displayName = languageManager.getItemName(nameKey, placeholders);
            meta.setDisplayName(displayName);
            List<Component> lore = languageManager.buildItemLoreAsComponents(
                    loreKey, placeholders, lootComponents, emptyLootKey);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            spawner.setItemMeta(meta);
        }
        ItemTooltipUtil.hideTooltip(spawner);
        if (amount == 1) {
            spawnerItemCache.put(entityType, spawner.clone());
            cacheTimestamps.put(entityType, System.currentTimeMillis());
            if (spawnerItemCache.size() > MAX_CACHE_SIZE) {
                EntityType oldestEntity = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<EntityType, Long> entry : cacheTimestamps.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestEntity = entry.getKey();
                    }
                }
                if (oldestEntity != null) {
                    spawnerItemCache.remove(oldestEntity);
                    cacheTimestamps.remove(oldestEntity);
                }
            }
        }
        return spawner;
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType) {
        return createVanillaSpawnerItem(entityType, 1);
    }

    public ItemStack createVanillaSpawnerItem(EntityType entityType, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", entityTypeName);
            placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityTypeName));
            String nameKey = itemKey("vanilla_spawner", entityType.name(), "name");
            String loreKey = itemKey("vanilla_spawner", entityType.name(), "lore");
            String displayName = languageManager.getItemName(nameKey, placeholders);
            if (displayName != null && !displayName.isEmpty() && !displayName.equals(nameKey)) {
                meta.setDisplayName(displayName);
            }
            List<String> lore = languageManager.getItemLoreWithMultilinePlaceholders(loreKey, placeholders);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                ItemTooltipUtil.hideTooltip(spawner);
            }
            meta.getPersistentDataContainer().set(
                    VANILLA_SPAWNER_KEY,
                    PersistentDataType.BOOLEAN,
                    true
            );
            spawner.setItemMeta(meta);
        }
        return spawner;
    }

    public ItemStack createItemSpawnerItem(Material itemMaterial) {
        return createItemSpawnerItem(itemMaterial, 1);
    }

    public ItemStack createItemSpawnerItem(Material itemMaterial, int amount) {
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && itemMaterial != null) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    // Set to ITEM type for item spawners
                    cs.setSpawnedType(EntityType.ITEM);
                    blockMeta.setBlockState(cs);
                }
            }
            
            String itemName = languageManager.getVanillaItemName(itemMaterial);
            String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
            
            // Get loot config for this item spawner
            EntityLootConfig lootConfig = plugin.getItemSpawnerSettingsConfig().getLootConfig(itemMaterial);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", itemName);
            placeholders.put("ᴇɴᴛɪᴛʏ", itemNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.experience() : 0));
            
            // Build loot items list similar to regular spawners
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.material().name()));
            String nameKey = itemKey("item_spawner", itemMaterial.name(), "name");
            String lootItemsKey = itemKey("item_spawner", itemMaterial.name(), "loot_items");
            String emptyLootKey = itemKey("item_spawner", itemMaterial.name(), "loot_items_empty");
            String loreKey = itemKey("item_spawner", itemMaterial.name(), "lore");
            // Build translatable loot lines – each player sees item names in their own client language
            List<Component> lootComponents = new ArrayList<>(sortedLootItems.size());
            for (LootItem item : sortedLootItems) {
                String amountRange = item.minAmount() == item.maxAmount() ?
                        String.valueOf(item.minAmount()) :
                        item.minAmount() + "-" + item.maxAmount();
                String chance = String.format("%.1f", item.chance());
                lootComponents.add(languageManager.buildTranslatableLootLine(
                        lootItemsKey, item.material(), amountRange, chance));
            }

            String displayName = languageManager.getItemName(nameKey, placeholders);
            if (displayName == null || displayName.isEmpty() || displayName.equals(nameKey)) {
                displayName = itemName;
            }
            meta.setDisplayName(displayName);

            List<Component> lore = languageManager.buildItemLoreAsComponents(
                    loreKey, placeholders, lootComponents, emptyLootKey);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            
            // Store the item material in persistent data
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "item_spawner_material"),
                    PersistentDataType.STRING,
                    itemMaterial.name()
            );
            
            spawner.setItemMeta(meta);
        }
        ItemTooltipUtil.hideTooltip(spawner);
        return spawner;
    }

    private String itemKey(String section, String variant, String field) {
        return languageManager.getItemVariantKey(section, variant, field);
    }
}
