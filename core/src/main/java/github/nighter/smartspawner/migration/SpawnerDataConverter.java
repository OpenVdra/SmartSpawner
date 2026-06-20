package github.nighter.smartspawner.migration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import github.nighter.smartspawner.SmartSpawner;
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
    private final SmartSpawner plugin;
    private final FileConfiguration oldConfig;
    private final FileConfiguration newConfig;
    private static final Gson GSON = new Gson();

    public SpawnerDataConverter(SmartSpawner plugin, FileConfiguration oldConfig, FileConfiguration newConfig) {
        this.plugin = plugin;
        this.oldConfig = oldConfig;
        this.newConfig = newConfig;
    }

    public void convertData() {
        ConfigurationSection spawnersSection = oldConfig.getConfigurationSection("spawners");
        if (spawnersSection == null) return;

        for (String spawnerId : spawnersSection.getKeys(false)) {
            try {
                convertSpawner(spawnerId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to convert spawner: " + spawnerId, e);
            }
        }
    }

    private void convertSpawner(String spawnerId) {
        String oldPath = "spawners." + spawnerId + ".";

        // Location einlesen
        String worldName = oldConfig.getString(oldPath + "world");
        int x = oldConfig.getInt(oldPath + "x");
        int y = oldConfig.getInt(oldPath + "y");
        int z = oldConfig.getInt(oldPath + "z");

        // Settings-String kompakt bauen
        String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%b",
                oldConfig.getInt(oldPath + "spawnerExp"),
                oldConfig.getBoolean(oldPath + "spawnerActive"),
                oldConfig.getInt(oldPath + "spawnerRange"),
                oldConfig.getBoolean(oldPath + "spawnerStop"),
                oldConfig.getInt(oldPath + "spawnDelay"),
                oldConfig.getInt(oldPath + "maxSpawnerLootSlots"),
                oldConfig.getInt(oldPath + "maxStoredExp"),
                oldConfig.getInt(oldPath + "minMobs"),
                oldConfig.getInt(oldPath + "maxMobs"),
                oldConfig.getInt(oldPath + "stackSize"),
                oldConfig.getLong(oldPath + "lastSpawnTime"),
                oldConfig.getBoolean(oldPath + "allowEquipmentItems")
        );

        // Inventar konvertieren
        List<String> newInventoryFormat = new ArrayList<>();
        ConfigurationSection invSection = oldConfig.getConfigurationSection(oldPath + "virtualInventory");
        
        if (invSection != null) {
            List<String> serializedItems = invSection.getStringList("items");
            Map<String, Map<Integer, Integer>> durabilityItems = new HashMap<>(); // Material -> (Durability -> Count)
            Map<String, Integer> regularItems = new HashMap<>();

            for (String serialized : serializedItems) {
                int colonIndex = serialized.indexOf(':');
                if (colonIndex == -1) continue;

                try {
                    ItemStack item = itemStackFromJson(serialized.substring(colonIndex + 1));
                    if (item == null || item.getType().isAir()) continue;

                    Material type = item.getType();

                    // Spezialfall: Tipped Arrows
                    if (type == Material.TIPPED_ARROW) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta instanceof PotionMeta potionMeta && potionMeta.hasCustomEffects()) {
                            PotionEffect effect = potionMeta.getCustomEffects().get(0);
                            String itemKey = String.format("TIPPED_ARROW#%s;%d;%d",
                                    effect.getType().getKey().getKey(),
                                    effect.getDuration(),
                                    effect.getAmplifier());
                            regularItems.merge(itemKey, item.getAmount(), Integer::sum);
                        } else {
                            regularItems.merge("ARROW", item.getAmount(), Integer::sum);
                        }
                    } 
                    // Items mit Haltbarkeit (Werkzeuge, Waffen, Rüstungen)
                    else if (type.getMaxDurability() > 0) {
                        int durability = 0;
                        if (item.getItemMeta() instanceof Damageable damageable) {
                            durability = damageable.getDamage();
                        }
                        durabilityItems.computeIfAbsent(type.name(), k -> new TreeMap<>())
                                .merge(durability, item.getAmount(), Integer::sum);
                    } 
                    // Normale Stack-Items
                    else {
                        regularItems.merge(type.name(), item.getAmount(), Integer::sum);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert item in spawner " + spawnerId + ": " + e.getMessage());
                }
            }

            // Normale Items ins neue Format schreiben
            for (Map.Entry<String, Integer> entry : regularItems.entrySet()) {
                newInventoryFormat.add(entry.getKey() + ":" + entry.getValue());
            }

            // Durability Items sortiert hinzufügen
            for (Map.Entry<String, Map<Integer, Integer>> itemEntry : durabilityItems.entrySet()) {
                StringBuilder itemString = new StringBuilder(itemEntry.getKey());
                if (!itemEntry.getValue().isEmpty()) {
                    itemString.append(";");
                    boolean first = true;
                    for (Map.Entry<Integer, Integer> durabilityEntry : itemEntry.getValue().entrySet()) {
                        if (!first) itemString.append(",");
                        itemString.append(durabilityEntry.getKey()).append(":").append(durabilityEntry.getValue());
                        first = false;
                    }
                }
                newInventoryFormat.add(itemString.toString());
            }
        }

        // Neues Daten-Layout sichern
        String spawnerPath = "spawners." + spawnerId;
        newConfig.set(spawnerPath + ".location", String.format("%s,%d,%d,%d", worldName, x, y, z));
        newConfig.set(spawnerPath + ".entityType", oldConfig.getString(oldPath + "entityType"));
        newConfig.set(spawnerPath + ".settings", settings);
        newConfig.set(spawnerPath + ".inventory", newInventoryFormat);
    }

    public static ItemStack itemStackFromJson(String data) {
        JsonObject json = GSON.fromJson(data, JsonObject.class);
        if (json == null || !json.has("type")) return null;

        Material material = Material.valueOf(json.get("type").getAsString());
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
        
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Haltbarkeit modern setzen
            if (json.has("durability") && meta instanceof Damageable damageable) {
                damageable.setDamage(json.get("durability").getAsInt());
            }

            if (json.has("displayName")) {
                meta.setDisplayName(json.get("displayName").getAsString());
            }

            if (json.has("lore")) {
                List<String> lore = new ArrayList<>();
                JsonArray loreArray = json.getAsJsonArray("lore");
                for (JsonElement
