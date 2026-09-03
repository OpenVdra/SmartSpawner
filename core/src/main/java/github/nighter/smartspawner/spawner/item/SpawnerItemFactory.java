package github.nighter.smartspawner.spawner.item;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.config.SpawnerDisplayConfigurator;
import github.nighter.smartspawner.spawner.utils.SpawnerDisplayName;
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
    private final NamespacedKey configNameKey;
    private final NamespacedKey itemSpawnerMaterialKey;
    private final Map<String, ItemStack> spawnerItemCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();

    public SpawnerItemFactory(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        VANILLA_SPAWNER_KEY = new NamespacedKey(plugin, "vanilla_spawner");
        this.configNameKey = new NamespacedKey(plugin, "spawner_config_name");
        this.itemSpawnerMaterialKey = new NamespacedKey(plugin, "item_spawner_material");
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
        Iterator<Map.Entry<String, Long>> iterator = cacheTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_EXPIRY_TIME_MS) {
                spawnerItemCache.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType) {
        return createSmartSpawnerItem(entityType, 1);
    }

    public ItemStack createSmartSpawnerItem(EntityType entityType, int amount) {
        var definition = plugin.getSpawnerSettingsConfig().getDefaultDefinition(entityType);
        return definition == null ? createSmartSpawnerItem(null, entityType, amount)
                : createSmartSpawnerItem(definition.name(), entityType, amount);
    }

    public ItemStack createSmartSpawnerItem(String configName, int amount) {
        var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
        if (definition == null) return null;
        return createSmartSpawnerItem(definition.name(), definition.entityType(), amount);
    }

    private ItemStack createSmartSpawnerItem(String configName, EntityType entityType, int amount) {
        cleanupCacheIfNeeded();
        String cacheKey = "mob:" + (configName != null ? configName : entityType.name());
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(cacheKey);
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
                    SpawnerDisplayConfigurator.applyMob(plugin, cs, configName, entityType);
                    blockMeta.setBlockState(cs);
                }
            }
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String entityTypeNameSmallCaps = languageManager.getSmallCaps(entityTypeName);
            var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
            EntityLootConfig lootConfig = definition != null ? definition.lootConfig()
                    : plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
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
                        lootItemsKey, item.template(), amountRange, chance));
            }
            String displayName = languageManager.getItemName(nameKey, placeholders);
            meta.setDisplayName(displayName);
            List<Component> lore = languageManager.buildItemLoreAsComponents(
                    loreKey, placeholders, lootComponents, emptyLootKey);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            if (configName != null) meta.getPersistentDataContainer().set(configNameKey, PersistentDataType.STRING, configName);
            spawner.setItemMeta(meta);
        }
        ItemTooltipUtil.hideTooltip(spawner);
        if (amount == 1) {
            spawnerItemCache.put(cacheKey, spawner.clone());
            cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            if (spawnerItemCache.size() > MAX_CACHE_SIZE) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestKey = entry.getKey();
                    }
                }
                if (oldestKey != null) {
                    spawnerItemCache.remove(oldestKey);
                    cacheTimestamps.remove(oldestKey);
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
        var definition = plugin.getItemSpawnerSettingsConfig().getDefaultDefinition(itemMaterial);
        return definition == null ? createItemSpawnerItem(null, itemMaterial, amount)
                : createItemSpawnerItem(definition.name(), itemMaterial, amount);
    }

    public ItemStack createItemSpawnerItem(String configName, int amount) {
        var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
        if (definition == null) return null;
        return createItemSpawnerItem(definition.name(), definition.material(), amount);
    }

    private ItemStack createItemSpawnerItem(String configName, Material itemMaterial, int amount) {
        cleanupCacheIfNeeded();
        String cacheKey = "item:" + (configName != null ? configName : itemMaterial.name());
        if (amount == 1) {
            ItemStack cachedItem = spawnerItemCache.get(cacheKey);
            if (cachedItem != null) return cachedItem.clone();
        }
        ItemStack spawner = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null && itemMaterial != null) {
            if (meta instanceof BlockStateMeta blockMeta) {
                BlockState blockState = blockMeta.getBlockState();
                if (blockState instanceof CreatureSpawner cs) {
                    SpawnerDisplayConfigurator.applyItem(plugin, cs, configName, itemMaterial);
                    blockMeta.setBlockState(cs);
                }
            }
            
            String itemName = SpawnerDisplayName.ofItem(plugin, configName, itemMaterial);
            String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
            
            // Get loot config for this item spawner
            EntityLootConfig lootConfig = plugin.getItemSpawnerSettingsConfig()
                    .getLootConfig(configName, itemMaterial);
            List<LootItem> lootItems = lootConfig != null ? lootConfig.getAllItems() : Collections.emptyList();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("entity", itemName);
            placeholders.put("ᴇɴᴛɪᴛʏ", itemNameSmallCaps);
            placeholders.put("exp", String.valueOf(lootConfig != null ? lootConfig.experience() : 0));
            
            // Build loot items list similar to regular spawners
            List<LootItem> sortedLootItems = new ArrayList<>(lootItems);
            sortedLootItems.sort(Comparator.comparing(item -> item.material().name()));
            String nameKey = itemKey("item_spawner", configName, itemMaterial.name(), "name");
            String lootItemsKey = itemKey("item_spawner", configName, itemMaterial.name(), "loot_items");
            String emptyLootKey = itemKey("item_spawner", configName, itemMaterial.name(), "loot_items_empty");
            String loreKey = itemKey("item_spawner", configName, itemMaterial.name(), "lore");
            // Build translatable loot lines – each player sees item names in their own client language
            List<Component> lootComponents = new ArrayList<>(sortedLootItems.size());
            for (LootItem item : sortedLootItems) {
                String amountRange = item.minAmount() == item.maxAmount() ?
                        String.valueOf(item.minAmount()) :
                        item.minAmount() + "-" + item.maxAmount();
                String chance = String.format("%.1f", item.chance());
                lootComponents.add(languageManager.buildTranslatableLootLine(
                        lootItemsKey, item.template(), amountRange, chance));
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
                    itemSpawnerMaterialKey,
                    PersistentDataType.STRING,
                    itemMaterial.name()
            );
            if (configName != null) meta.getPersistentDataContainer().set(configNameKey, PersistentDataType.STRING, configName);
            
            spawner.setItemMeta(meta);
        }
        ItemTooltipUtil.hideTooltip(spawner);
        if (amount == 1) {
            spawnerItemCache.put(cacheKey, spawner.clone());
            cacheTimestamps.put(cacheKey, System.currentTimeMillis());
            trimCacheToLimit();
        }
        return spawner;
    }

    private void trimCacheToLimit() {
        if (spawnerItemCache.size() <= MAX_CACHE_SIZE) return;
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            spawnerItemCache.remove(oldestKey);
            cacheTimestamps.remove(oldestKey);
        }
    }

    private String itemKey(String section, String variant, String field) {
        return languageManager.getItemVariantKey(section, variant, field);
    }

    private String itemKey(String section, String variant, String fallbackVariant, String field) {
        return variant == null
                ? languageManager.getItemVariantKey(section, fallbackVariant, field)
                : languageManager.getItemVariantKey(section, variant, fallbackVariant, field);
    }
}
