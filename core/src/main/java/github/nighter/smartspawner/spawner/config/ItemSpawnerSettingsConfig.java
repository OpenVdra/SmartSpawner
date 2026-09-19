package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the item spawner settings configuration
 */
public class ItemSpawnerSettingsConfig {
    private static final String RESOURCE = "spawner_items.yml";
    /** Replaced by {@link #RESOURCE} in 1.8.0. Never read, only reported once. */
    private static final String LEGACY_RESOURCE = "item_spawners_settings.yml";

    private final SmartSpawner plugin;
    private FileConfiguration config;
    private final File configFile;

    /**
     * Every lookup takes the spawner name first and its base material only as a fallback, because
     * several entries can share one material once an entry names an item another plugin owns.
     * {@link #defaultDefinitionsByMaterial} is the bridge for spawners placed before names existed.
     */
    private final Map<String, ItemHeadData> headDataByName = new HashMap<>();
    private final Map<String, EntityLootConfig> lootConfigsByName = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> displayItemsByName = new HashMap<>();
    private final Map<String, ItemDefinition> definitionsByName = new HashMap<>();
    private final Map<Material, ItemDefinition> defaultDefinitionsByMaterial = new EnumMap<>(Material.class);

    public ItemSpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), RESOURCE);
    }

    /**
     * Load or create the item spawners settings configuration
     */
    public void load() {
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            saveDefaultConfig();
            SupersededConfigNotice.warn(plugin, RESOURCE, LEGACY_RESOURCE);
        }

        // Load the configuration
        config = YamlConfiguration.loadConfiguration(configFile);

        // Parse configuration
        parseConfig();
    }

    /**
     * Save the default configuration from resources
     */
    private void saveDefaultConfig() {
        try {
            InputStream inputStream = plugin.getResource(RESOURCE);
            if (inputStream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default spawner_items.yml: " + e.getMessage());
        }
    }

    /**
     * Parse the configuration and populate item head data
     */
    private void parseConfig() {
        headDataByName.clear();
        lootConfigsByName.clear();
        displayItemsByName.clear();
        definitionsByName.clear();
        defaultDefinitionsByMaterial.clear();

        // Parse each item's configuration
        for (String configName : config.getKeys(false)) {
            // Anything that is not a section is a stray scalar, not an entry.
            ConfigurationSection itemSection = config.getConfigurationSection(configName);
            if (itemSection == null) continue;

            ItemStack template;
            try {
                template = ConfiguredItemParser.parse(itemSection.getString("item", configName));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Item for '" + configName + "' is invalid or missing in "
                        + RESOURCE + ": " + e.getMessage());
                continue;
            }
            Material material = template.getType();

            String normalizedName = itemSection.contains("item")
                    ? SpawnerConfigName.normalize(configName)
                    : SpawnerConfigName.defaultName(material.name());
            if (normalizedName.isEmpty() || definitionsByName.containsKey(normalizedName)) {
                plugin.getLogger().warning("Duplicate or invalid spawner name '" + configName + "' in " + RESOURCE);
                continue;
            }

            // Parse head texture data
            parseHeadTexture(normalizedName, material, itemSection);
            ItemStack displayItem = parseDisplayItem(normalizedName, itemSection);

            // Parse loot data
            parseLootData(normalizedName, itemSection);

            ItemDefinition definition = new ItemDefinition(normalizedName, material, template,
                    lootConfigsByName.get(normalizedName));
            definitionsByName.put(normalizedName, definition);
            if (displayItem != null) {
                displayItemsByName.put(normalizedName, displayItem);
            }
            defaultDefinitionsByMaterial.putIfAbsent(material, definition);
        }
    }

    private ItemStack parseDisplayItem(String configName, ConfigurationSection itemSection) {
        String rawItem = itemSection.getString("nbt_data");
        if (rawItem == null || rawItem.isBlank()) {
            return null;
        }
        try {
            return ConfiguredItemParser.parse(rawItem).asQuantity(1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid nbt_data for " + configName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse loot configuration for an item spawner
     */
    private void parseLootData(String configName, ConfigurationSection itemSection) {
        int experience = itemSection.getInt("experience", 0);
        List<LootItem> items = new ArrayList<>();

        ConfigurationSection lootSection = itemSection.getConfigurationSection("loot");
        if (lootSection != null) {
            for (String itemKey : lootSection.getKeys(false)) {
                ConfigurationSection lootItemSection = lootSection.getConfigurationSection(itemKey);
                if (lootItemSection == null) continue;

                LootItem lootItem = LootEntryParser.parse(
                        lootItemSection, itemKey, plugin.getItemPriceManager(), plugin.getLogger(),
                        "item spawner " + configName);
                if (lootItem != null) {
                    items.add(lootItem);
                }
            }
        }

        // Create and store EntityLootConfig
        EntityLootConfig lootConfig = new EntityLootConfig(experience, items);
        lootConfigsByName.put(configName, lootConfig);
    }

    /**
     * Parse head texture configuration for an item
     */
    private void parseHeadTexture(String configName, Material material, ConfigurationSection itemSection) {
        ConfigurationSection headSection = itemSection.getConfigurationSection("mob_head");
        if (headSection == null) {
            return;
        }

        String headMaterialName = headSection.getString("item", material.name());
        String customTexture = headSection.getString("hash_texture");

        // Validate material
        Material headMaterial;
        try {
            headMaterial = Material.valueOf(headMaterialName.toUpperCase());
            if (!headMaterial.isItem()) {
                plugin.getLogger().warning("Material " + headMaterialName + " for " + configName + " is not an item, using the item itself");
                headMaterial = material;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid head material " + headMaterialName + " for " + configName + ", using the item itself");
            headMaterial = material;
        }

        // Store item head data
        headDataByName.putIfAbsent(configName, new ItemHeadData(headMaterial, customTexture));
    }

    private ItemDefinition resolve(String configName, Material fallback) {
        ItemDefinition definition = definitionsByName.get(SpawnerConfigName.normalize(configName));
        if (definition != null) {
            return definition;
        }
        return fallback == null ? null : defaultDefinitionsByMaterial.get(fallback);
    }

    /**
     * Get the head texture data for an item spawner, or null when the entry configures no head. A
     * null answer means the produced item is shown as it is.
     */
    public ItemHeadData getHeadData(String configName, Material fallback) {
        ItemDefinition definition = resolve(configName, fallback);
        return definition == null ? null : headDataByName.get(definition.name());
    }

    /**
     * Get the loot configuration for an item spawner
     */
    public EntityLootConfig getLootConfig(String configName, Material fallback) {
        ItemDefinition definition = resolve(configName, fallback);
        return definition == null ? null : definition.lootConfig();
    }

    /** The item this spawner produces, which is what names and pictures it. */
    public ItemStack getTemplate(String configName, Material fallback) {
        ItemDefinition definition = resolve(configName, fallback);
        if (definition != null) {
            return definition.template().asQuantity(1);
        }
        return fallback == null ? null : new ItemStack(fallback, 1);
    }

    /** Uses the first configured loot template as the model rendered inside the spawner cage. */
    public ItemStack getDisplayItem(String configName, Material fallback) {
        ItemDefinition definition = resolve(configName, fallback);
        if (definition == null) {
            return fallback == null ? null : new ItemStack(fallback, 1);
        }
        ItemStack configured = displayItemsByName.get(definition.name());
        if (configured != null) {
            return configured.clone();
        }
        if (definition.lootConfig() != null) {
            for (LootItem lootItem : definition.lootConfig().getAllItems()) {
                if (lootItem.template() != null) return lootItem.template().asQuantity(1);
            }
        }
        return definition.template().asQuantity(1);
    }

    public ItemDefinition getDefinition(String name) {
        return definitionsByName.get(SpawnerConfigName.normalize(name));
    }

    public ItemDefinition getDefaultDefinition(Material material) {
        return material == null ? null : defaultDefinitionsByMaterial.get(material);
    }

    public Set<String> getDefinitionNames() {
        return Collections.unmodifiableSet(definitionsByName.keySet());
    }

    public record ItemDefinition(String name, Material material, ItemStack template, EntityLootConfig lootConfig) {}

    /**
     * Reload the configuration
     */
    public void reload() {
        load();
    }

    /**
     * Data class for item head information
     */
    public static class ItemHeadData {
        private final Material material;
        private final String customTexture;

        public ItemHeadData(Material material, String customTexture) {
            this.material = material;
            this.customTexture = customTexture;
        }

        public Material getMaterial() {
            return material;
        }

        public String getCustomTexture() {
            return customTexture;
        }

        public boolean hasCustomTexture() {
            return customTexture != null && !customTexture.isEmpty() && !customTexture.equalsIgnoreCase("null");
        }
    }
}
