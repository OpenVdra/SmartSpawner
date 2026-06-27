package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.updates.Version;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the merged spawner settings configuration that combines mob drops and head textures
 */
public class SpawnerSettingsConfig {
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;
    private static final String CONFIG_VERSION_KEY = "config_version";
    private final String CURRENT_CONFIG_VERSION;
    
    // Mob head data
    private Material defaultMaterial;
    private final Map<EntityType, MobHeadData> mobHeadMap = new EnumMap<>(EntityType.class);
    
    // Loot data
    private final Map<String, EntityLootConfig> entityLootConfigs = new ConcurrentHashMap<>();
    private final Set<Material> loadedMaterials = new HashSet<>();

    // Spawner item drop chance when the spawner block is broken
    private final Map<EntityType, Double> spawnerDropChances = new EnumMap<>(EntityType.class);
    
    public SpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "spawners_settings.yml");
        CURRENT_CONFIG_VERSION = plugin.getPluginMeta().getVersion();
    }
    
    /**
     * Load or create the spawners settings configuration
     */
    public void load() {
        
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        
        // Check and update config version
        checkAndUpdateConfig();
        
        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Parse configuration
        parseConfig();
    }

    /**
     * Check if config needs updating and update if necessary
     */
    private void checkAndUpdateConfig() {
        if (!configFile.exists()) {
            return;
        }
        
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        String configVersionStr = currentConfig.getString(CONFIG_VERSION_KEY, "0.0.0");
        Version configVersion = new Version(configVersionStr);
        Version currentConfigVersion = new Version(CURRENT_CONFIG_VERSION);

        if (configVersion.compareTo(currentConfigVersion) >= 0) {
            return;
        }
        
        plugin.getLogger().info("Updating spawners_settings.yml from version " + configVersionStr + " to " + CURRENT_CONFIG_VERSION);

        try {
            Map<String, Object> userValues = flattenConfig(currentConfig);

            // Create temp file with new default config
            File tempFile = new File(plugin.getDataFolder(), "spawners_settings_new.yml");
            createDefaultConfigWithHeader(tempFile);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION);

            // Check if there are actual differences before creating backup
            boolean configDiffers = hasConfigDifferences(userValues, newConfig);

            if (configDiffers) {
                File backupFile = new File(plugin.getDataFolder(), "spawners_settings_backup_" + configVersionStr + ".yml");
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Config backup created at " + backupFile.getName());
            } else {
                plugin.debug("No significant config changes detected, skipping backup creation");
            }
            
            // Apply user values and save
            applyUserValues(newConfig, userValues);
            newConfig.save(configFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update spawners_settings.yml", e);
        }
    }
    
    /**
     * Create default configuration file with version header
     */
    private void createDefaultConfigWithHeader(File destinationFile) {
        try {
            // Ensure parent directory exists
            File parentDir = destinationFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (InputStream in = plugin.getResource("spawners_settings.yml")) {
                if (in != null) {
                    List<String> defaultLines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .toList();

                    List<String> newLines = new ArrayList<>();
                    newLines.add("# Configuration version - Do not modify this value");
                    newLines.add(CONFIG_VERSION_KEY + ": " + CURRENT_CONFIG_VERSION);
                    newLines.add("");
                    newLines.addAll(defaultLines);

                    Files.write(destinationFile.toPath(), newLines, StandardCharsets.UTF_8);
                } else {
                    plugin.getLogger().warning("Default spawners_settings.yml not found in the plugin's resources.");
                    destinationFile.createNewFile();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default spawners_settings.yml with header: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if there are actual differences between old and new config
     */
    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        // Get all paths from new config (excluding config_version)
        Map<String, Object> newConfigMap = flattenConfig(newConfig);

        // Check for removed or changed keys
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object oldValue = entry.getValue();

            // Skip config_version key
            if (path.equals(CONFIG_VERSION_KEY)) continue;

            // Check if path no longer exists
            if (!newConfig.contains(path)) {
                return true; // Found a removed path
            }

            // Check if default value changed
            Object newDefaultValue = newConfig.get(path);
            if (newDefaultValue != null && !newDefaultValue.equals(oldValue)) {
                return true; // Default value changed
            }
        }

        // Check for new keys
        for (String path : newConfigMap.keySet()) {
            if (!path.equals(CONFIG_VERSION_KEY) && !userValues.containsKey(path)) {
                return true; // Found a new path
            }
        }

        return false; // No significant differences
    }

    /**
     * Flattens a configuration section into a map of path -> value
     */
    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    /**
     * Applies the user values to the new config
     */
    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            // Don't override config_version
            if (path.equals(CONFIG_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else if (isOptionalDropChancePath(path, newConfig)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().warning("Config path '" + path + "' from old config no longer exists in new config");
            }
        }
    }

    private boolean isOptionalDropChancePath(String path, FileConfiguration newConfig) {
        if (!path.endsWith(".drop_chance")) {
            return false;
        }

        String entityName = path.substring(0, path.length() - ".drop_chance".length());
        return newConfig.isConfigurationSection(entityName);
    }

    /**
     * Save the default configuration from resources (legacy method for initial file creation)
     */
    private void saveDefaultConfig() {
        if (!configFile.exists()) {
            createDefaultConfigWithHeader(configFile);
            plugin.getLogger().info("Created default spawners_settings.yml configuration");
        }
    }
    
    /**
     * Parse the configuration and populate both mob head and loot data
     */
    private void parseConfig() {
        mobHeadMap.clear();
        entityLootConfigs.clear();
        loadedMaterials.clear();
        spawnerDropChances.clear();
        
        // Get default material
        String defaultMaterialName = config.getString("default_material", "SPAWNER");
        try {
            defaultMaterial = Material.valueOf(defaultMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid default_material in spawners_settings.yml: " + defaultMaterialName + ", using SPAWNER");
            defaultMaterial = Material.SPAWNER;
        }
        
        // Parse each mob's configuration
        for (String entityName : config.getKeys(false)) {
            // Skip special keys
            if (entityName.equals(CONFIG_VERSION_KEY) || entityName.equals("default_material")) {
                continue;
            }
            
            // Validate entity type
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Entity type '" + entityName + "' is invalid or not available in server version " + plugin.getServer().getBukkitVersion());
                continue;
            }
            
            ConfigurationSection entitySection = config.getConfigurationSection(entityName);
            if (entitySection == null) continue;
            
            // Parse head texture data
            parseHeadTexture(entityType, entitySection);
            
            // Parse loot data
            parseLootData(entityName, entitySection);

            parseSpawnerDropChance(entityType, entitySection);
        }
    }

    private void parseSpawnerDropChance(EntityType entityType, ConfigurationSection entitySection) {
        if (!entitySection.contains("drop_chance")) {
            return;
        }

        double dropChance = entitySection.getDouble("drop_chance", 100.0);
        if (dropChance < 0.0 || dropChance > 100.0) {
            plugin.getLogger().warning("Invalid drop_chance for " + entityType.name() +
                    " in spawners_settings.yml. Value must be between 0.0 and 100.0; using 100.0");
            dropChance = 100.0;
        }

        spawnerDropChances.put(entityType, dropChance);
    }
    
    /**
     * Parse head texture configuration for an entity
     */
    private void parseHeadTexture(EntityType entityType, ConfigurationSection entitySection) {
        ConfigurationSection headSection = entitySection.getConfigurationSection("head_texture");
        if (headSection == null) {
            return;
        }
        
        String materialName = headSection.getString("material", "SPAWNER");
        String customTexture = headSection.getString("custom_texture");
        
        // Validate material
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
            if (!material.isItem()) {
                plugin.getLogger().warning("Material " + materialName + " for " + entityType + " is not an item, using default");
                material = defaultMaterial;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material " + materialName + " for " + entityType + ", using default");
            material = defaultMaterial;
        }
        
        // Store mob head data
        mobHeadMap.put(entityType, new MobHeadData(material, customTexture));
    }
    
    /**
     * Parse loot configuration for an entity
     */
    private void parseLootData(String entityName, ConfigurationSection entitySection) {
        int experience = entitySection.getInt("experience", 0);
        List<LootItem> items = new ArrayList<>();
        
        // Cache price manager reference for better performance
        ItemPriceManager priceManager = plugin.getItemPriceManager();
        
        ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                if (itemSection == null) continue;
                
                try {
                    // Get the material
                    Material material;
                    try {
                        material = Material.valueOf(itemKey.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = null;
                    }
                    
                    if (material == null) {
                        plugin.getLogger().warning("Material '" + itemKey + "' is not available in server version " +
                                plugin.getServer().getBukkitVersion() + " - skipping for entity " + entityName);
                        continue;
                    }
                    
                    loadedMaterials.add(material);
                    
                    String[] amounts = itemSection.getString("amount", "1-1").split("-");
                    int minAmount = Integer.parseInt(amounts[0]);
                    int maxAmount = Integer.parseInt(amounts.length > 1 ? amounts[1] : amounts[0]);
                    double chance = itemSection.getDouble("chance", 100.0);
                    
                    double sellPrice = 0.0;
                    if (priceManager != null) {
                        sellPrice = priceManager.getPrice(material);
                    }
                    
                    Integer minDurability = null;
                    Integer maxDurability = null;
                    if (itemSection.contains("durability")) {
                        String[] durabilities = itemSection.getString("durability").split("-");
                        minDurability = Integer.parseInt(durabilities[0]);
                        maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                    }
                    
                    PotionType potionType = null;
                    if (material == Material.TIPPED_ARROW && itemSection.contains("potion_type")) {
                        String potionTypeName = itemSection.getString("potion_type");
                        if (potionTypeName != null) {
                            try {
                                potionType = PotionType.valueOf(potionTypeName.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid potion type '" + potionTypeName +
                                        "' for entity " + entityName);
                                continue;
                            }
                        }
                    }
                    
                    items.add(new LootItem(material, minAmount, maxAmount, chance,
                            minDurability, maxDurability, potionType, sellPrice));
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing material '" + itemKey + "' for entity " + entityName + ": " + e.getMessage());
                }
            }
        }
        
        entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
    }
    
    // ===== Mob Head Methods =====
    
    /**
     * Get the material for a specific entity type
     */
    public Material getMaterial(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.material : defaultMaterial;
    }
    
    /**
     * Get the custom texture for a specific entity type
     */
    public String getCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null ? data.customTexture : null;
    }
    
    /**
     * Check if an entity type has a custom texture configured
     */
    public boolean hasCustomTexture(EntityType entityType) {
        MobHeadData data = mobHeadMap.get(entityType);
        return data != null && data.customTexture != null && !data.customTexture.isEmpty();
    }
    
    // ===== Loot Methods =====
    
    /**
     * Get loot configuration for an entity type
     */
    public EntityLootConfig getLootConfig(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }
        return entityLootConfigs.get(entityType.name().toLowerCase());
    }

    /**
     * Get the spawner item drop chance for a broken Smart Spawner.
     */
    public double getSpawnerDropChance(EntityType entityType) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return 100.0;
        }
        return spawnerDropChances.getOrDefault(entityType, 100.0);
    }

    /**
     * Check whether an entity has an explicit spawner item drop chance configured.
     */
    public boolean hasSpawnerDropChance(EntityType entityType) {
        return entityType != null && entityType != EntityType.UNKNOWN && spawnerDropChances.containsKey(entityType);
    }
    
    /**
     * Get all loaded materials
     */
    public Set<Material> getLoadedMaterials() {
        return new HashSet<>(loadedMaterials);
    }
    
    /**
     * Reload the configuration
     */
    public void reload() {
        load();
    }
    
    /**
     * Internal class to store mob head data
     */
    private static class MobHeadData {
        final Material material;
        final String customTexture;
        
        MobHeadData(Material material, String customTexture) {
            this.material = material;
            this.customTexture = customTexture;
        }
    }
}
