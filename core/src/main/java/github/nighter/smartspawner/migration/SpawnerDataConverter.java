package github.nighter.smartspawner.migration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.utils.ItemStackSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.logging.Level;

public class SpawnerDataConverter {
    private static final Gson GSON = new Gson();

    private final SmartSpawner plugin;
    private final FileConfiguration oldConfig;
    private final FileConfiguration newConfig;

    public SpawnerDataConverter(SmartSpawner plugin, FileConfiguration oldConfig, FileConfiguration newConfig) {
        this.plugin = plugin;
        this.oldConfig = oldConfig;
        this.newConfig = newConfig;
    }

    public void convertData() {
        ConfigurationSection spawnersSection = oldConfig.getConfigurationSection("spawners");
        if (spawnersSection == null) {
            return;
        }

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                convertSpawner(spawnerId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to convert spawner " + spawnerId, e);
            }
        }
    }

    private void convertSpawner(String spawnerId) {
        String oldPath = "spawners." + spawnerId;

        String worldName = oldConfig.getString(oldPath + ".world");
        int x = oldConfig.getInt(oldPath + ".x");
        int y = oldConfig.getInt(oldPath + ".y");
        int z = oldConfig.getInt(oldPath + ".z");

        String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%b",
                oldConfig.getInt(oldPath + ".spawnerExp"),
                oldConfig.getBoolean(oldPath + ".spawnerActive"),
                oldConfig.getInt(oldPath + ".spawnerRange"),
                oldConfig.getBoolean(oldPath + ".spawnerStop"),
                oldConfig.getInt(oldPath + ".spawnDelay"),
                oldConfig.getInt(oldPath + ".maxSpawnerLootSlots"),
                oldConfig.getInt(oldPath + ".maxStoredExp"),
                oldConfig.getInt(oldPath + ".minMobs"),
                oldConfig.getInt(oldPath + ".maxMobs"),
                oldConfig.getInt(oldPath + ".stackSize"),
                oldConfig.getLong(oldPath + ".lastSpawnTime"),
                oldConfig.getBoolean(oldPath + ".allowEquipmentItems")
        );

        List<String> newInventoryFormat = new ArrayList<>();
        ConfigurationSection invSection = oldConfig.getConfigurationSection(oldPath + ".virtualInventory");
        if (invSection != null) {
            List<String> serializedItems = invSection.getStringList("items");
            Map<String, Map<Integer, Integer>> durabilityItems = new HashMap<>();
            Map<String, Integer> regularItems = new HashMap<>();

            for (String serialized : serializedItems) {
                int colonIndex = serialized.indexOf(':');
                if (colonIndex == -1) {
                    continue;
                }

                try {
                    ItemStack item = itemStackFromJson(serialized.substring(colonIndex + 1));
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }

                    Material type = item.getType();

                    if (type == Material.TIPPED_ARROW) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta instanceof PotionMeta potionMeta && potionMeta.hasCustomEffects()) {
                            PotionEffect effect = potionMeta.getCustomEffects().get(0);
                            String itemKey = String.format("TIPPED_ARROW#%s;%d;%d",
                                    effect.getType().getName(),
                                    effect.getDuration(),
                                    effect.getAmplifier());
                            regularItems.merge(itemKey, item.getAmount(), Integer::sum);
                        } else {
                            regularItems.merge("ARROW", item.getAmount(), Integer::sum);
                        }
                    } else if (ItemStackSerializer.isDestructibleItem(type)) {
                        int damage = 0;
                        if (item.getItemMeta() instanceof Damageable damageable) {
                            damage = damageable.getDamage();
                        }
                        durabilityItems.computeIfAbsent(type.name(), k -> new TreeMap<>())
                                .merge(damage, item.getAmount(), Integer::sum);
                    } else {
                        regularItems.merge(type.name(), item.getAmount(), Integer::sum);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert item in spawner " + spawnerId + ": " + e.getMessage());
                }
            }

            for (Map.Entry<String, Integer> entry : regularItems.entrySet()) {
                newInventoryFormat.add(entry.getKey() + ":" + entry.getValue());
            }

            for (Map.Entry<String, Map<Integer, Integer>> itemEntry : durabilityItems.entrySet()) {
                StringBuilder itemString = new StringBuilder(itemEntry.getKey());
                if (!itemEntry.getValue().isEmpty()) {
                    itemString.append(";");
                    boolean first = true;
                    for (Map.Entry<Integer, Integer> durabilityEntry : itemEntry.getValue().entrySet()) {
                        if (!first) {
                            itemString.append(",");
                        }
                        itemString.append(durabilityEntry.getKey())
                                .append(":")
                                .append(durabilityEntry.getValue());
                        first = false;
                    }
                }
                newInventoryFormat.add(itemString.toString());
            }
        }

        String spawnerPath = "spawners." + spawnerId;
        newConfig.set(spawnerPath + ".location", String.format("%s,%d,%d,%d", worldName, x, y, z));
        newConfig.set(spawnerPath + ".entityType", oldConfig.getString(oldPath + ".entityType"));
        newConfig.set(spawnerPath + ".settings", settings);
        newConfig.set(spawnerPath + ".inventory", newInventoryFormat);
    }

    public static ItemStack itemStackFromJson(String data) {
        JsonObject json = GSON.fromJson(data, JsonObject.class);
        if (json == null || !json.has("type")) {
            return null;
        }

        Material material = Material.valueOf(json.get("type").getAsString());
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (json.has("durability") && meta instanceof Damageable damageable) {
            damageable.setDamage(json.get("durability").getAsInt());
            meta = damageable;
        }

        if (json.has("displayName")) {
            meta.setDisplayName(json.get("displayName").getAsString());
        }

        if (json.has("lore")) {
            List<String> lore = new ArrayList<>();
            JsonArray loreArray = json.getAsJsonArray("lore");
            for (JsonElement element : loreArray) {
                lore.add(element.getAsString());
            }
            meta.setLore(lore);
        }

        if (json.has("enchantments")) {
            JsonObject enchants = json.getAsJsonObject("enchantments");
            for (Map.Entry<String, JsonElement> entry : enchants.entrySet()) {
                Enchantment enchantment = resolveEnchantment(entry.getKey());
                if (enchantment != null) {
                    meta.addEnchant(enchantment, entry.getValue().getAsInt(), true);
                }
            }
        }

        if (meta instanceof PotionMeta potionMeta && json.has("potionData")) {
            JsonObject potionData = json.getAsJsonObject("potionData");

            if (potionData.has("customEffects")) {
                JsonArray customEffects = potionData.getAsJsonArray("customEffects");
                for (JsonElement element : customEffects) {
                    JsonObject effectObj = element.getAsJsonObject();
                    PotionEffectType type = resolvePotionEffectType(effectObj.get("type").getAsString());
                    if (type != null) {
                        PotionEffect effect = new PotionEffect(
                                type,
                                effectObj.get("duration").getAsInt(),
                                effectObj.get("amplifier").getAsInt(),
                                effectObj.get("ambient").getAsBoolean(),
                                effectObj.get("particles").getAsBoolean(),
                                effectObj.get("icon").getAsBoolean()
                        );
                        potionMeta.addCustomEffect(effect, true);
                    }
                }
            }
            meta = potionMeta;
        }

        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment resolveEnchantment(String key) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
        if (enchantment != null) {
            return enchantment;
        }
        enchantment = Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
        if (enchantment != null) {
            return enchantment;
        }
        return Enchantment.getByName(key);
    }

    private static PotionEffectType resolvePotionEffectType(String id) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
        if (type != null) {
            return type;
        }
        type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.fromString(id));
        if (type != null) {
            return type;
        }
        return PotionEffectType.getByName(id);
    }
}
