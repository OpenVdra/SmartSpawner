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

/**
 * Loads {@code spawner_items.yml}: one base spawner per item, keyed by the item itself, plus any
 * number of custom spawners under {@code custom_spawners}. See {@link SpawnerFileLayout} for the shape.
 *
 * <p>A custom spawner inherits its head, {@code nbt_data} and {@code experience} from the base spawner
 * of the same item when it leaves them out. Its loot is never inherited.</p>
 */
public class ItemSpawnerSettingsConfig {
    private static final String RESOURCE = "spawner_items.yml";
    /** Replaced by {@link #RESOURCE} in 1.8.0. Never read, only reported once. */
    private static final String LEGACY_RESOURCE = "item_spawners_settings.yml";
    private static final String TYPE_KEY = "item";

    private final SmartSpawner plugin;
    private final File configFile;

    private final Map<String, ItemDefinition> definitionsByName = new LinkedHashMap<>();
    private final Map<String, String> aliasToName = new HashMap<>();
    private final Map<Material, ItemDefinition> baseDefinitions = new EnumMap<>(Material.class);
    private final Map<String, String> sectionPaths = new HashMap<>();

    public ItemSpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), RESOURCE);
    }

    /**
     * Load or create the item spawners settings configuration
     */
    public void load() {
        if (!configFile.exists()) {
            saveDefaultConfig();
            SupersededConfigNotice.warn(plugin, RESOURCE, LEGACY_RESOURCE);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (SpawnerFileLayout.isLegacy(config, TYPE_KEY)) {
            SpawnerFileLayout.backup(configFile, plugin.getLogger());
            SpawnerFileLayout.convertLegacy(config, TYPE_KEY, ItemSpawnerSettingsConfig::baseIdOf);
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save converted " + RESOURCE + ": " + e.getMessage());
            }
        }

        parseConfig(config);
    }

    public void reload() {
        load();
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

    private static Material materialOf(String raw) {
        String name = SpawnerConfigName.normalize(raw).toUpperCase(Locale.ROOT);
        if (name.isEmpty()) return null;
        Material material = Material.getMaterial(name);
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    private static String baseIdOf(String raw) {
        Material material = materialOf(raw);
        return material == null ? null : baseId(material);
    }

    private static String baseId(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private void parseConfig(FileConfiguration config) {
        SpawnerNameNotices.reset();
        definitionsByName.clear();
        aliasToName.clear();
        baseDefinitions.clear();
        sectionPaths.clear();

        for (String key : config.getKeys(false)) {
            if (key.equals(SpawnerFileLayout.CUSTOM_SECTION)) continue;
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            Material material = materialOf(key);
            if (material == null) {
                plugin.getLogger().warning("'" + key + "' in " + RESOURCE + " is not an item name. Custom spawners go under "
                        + SpawnerFileLayout.CUSTOM_SECTION + ".");
                continue;
            }
            String id = baseId(material);
            if (definitionsByName.containsKey(id)) {
                plugin.getLogger().warning("'" + key + "' in " + RESOURCE + " repeats the " + id + " spawner and is ignored.");
                continue;
            }
            register(parseEntry(id, material, section, true, null), key);
        }

        ConfigurationSection customs = config.getConfigurationSection(SpawnerFileLayout.CUSTOM_SECTION);
        if (customs != null) {
            for (String key : customs.getKeys(false)) {
                ConfigurationSection section = customs.getConfigurationSection(key);
                if (section == null) continue;
                String id = SpawnerConfigName.normalize(key);
                String label = "custom spawner '" + key + "' in " + RESOURCE;
                if (id.isEmpty() || materialOf(id) != null) {
                    plugin.getLogger().warning(label + " uses an item name. Give it a name of its own.");
                    continue;
                }
                if (definitionsByName.containsKey(id)) {
                    plugin.getLogger().warning(label + " repeats another spawner's name and is ignored.");
                    continue;
                }
                Material material = materialOf(section.getString(TYPE_KEY, ""));
                if (material == null) {
                    plugin.getLogger().warning(label + " needs a valid '" + TYPE_KEY + "'.");
                    continue;
                }
                register(parseEntry(id, material, section, false, baseDefinitions.get(material)),
                        SpawnerFileLayout.CUSTOM_SECTION + "." + key);
            }
        }

        for (ItemDefinition definition : definitionsByName.values()) {
            for (String alias : definition.aliases()) {
                String name = SpawnerConfigName.normalize(alias);
                if (name.isEmpty() || definitionsByName.containsKey(name) || aliasToName.containsKey(name)) {
                    plugin.getLogger().warning("Alias '" + alias + "' of " + definition.name() + " in " + RESOURCE
                            + " is already a spawner name and is ignored.");
                    continue;
                }
                aliasToName.put(name, definition.name());
            }
        }
    }

    private void register(ItemDefinition definition, String sectionPath) {
        definitionsByName.put(definition.name(), definition);
        sectionPaths.put(definition.name(), sectionPath);
        if (definition.base()) {
            baseDefinitions.put(definition.material(), definition);
        }
    }

    private ItemDefinition parseEntry(String id, Material material, ConfigurationSection section, boolean base,
                                      ItemDefinition inherited) {
        String label = id + " in " + RESOURCE;

        int experience = section.contains("experience") || inherited == null
                ? section.getInt("experience", 0)
                : inherited.lootConfig().experience();
        EntityLootConfig lootConfig = new EntityLootConfig(experience, parseLoot(section, id));

        SpawnerHead head = SpawnerHead.parse(section, material, label, plugin.getLogger());
        if (head == null) head = inherited != null ? inherited.head() : new SpawnerHead(material, null);

        ItemStack displayItem = section.contains("nbt_data") || inherited == null
                ? parseDisplayItem(section, label)
                : inherited.displayItem();

        String displayName = section.getString("display_name");
        if (displayName != null && displayName.isBlank()) displayName = null;

        return new ItemDefinition(id, material, base, displayName, lootConfig, head, displayItem,
                List.copyOf(section.getStringList(SpawnerFileLayout.ALIASES)));
    }

    private List<LootItem> parseLoot(ConfigurationSection section, String id) {
        List<LootItem> items = new ArrayList<>();
        ConfigurationSection lootSection = section.getConfigurationSection("loot");
        if (lootSection == null) {
            return items;
        }
        for (String itemKey : lootSection.getKeys(false)) {
            ConfigurationSection lootItemSection = lootSection.getConfigurationSection(itemKey);
            if (lootItemSection == null) continue;
            LootItem lootItem = LootEntryParser.parse(lootItemSection, itemKey, plugin.getItemPriceManager(),
                    plugin.getLogger(), "item spawner " + id);
            if (lootItem != null) {
                items.add(lootItem);
            }
        }
        return items;
    }

    private ItemStack parseDisplayItem(ConfigurationSection section, String label) {
        String rawItem = section.getString("nbt_data");
        if (rawItem == null || rawItem.isBlank()) {
            return null;
        }
        try {
            return ConfiguredItemParser.parse(rawItem).asQuantity(1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid nbt_data for " + label + ": " + e.getMessage());
            return null;
        }
    }

    // ===== Lookups =====

    /** The spawner with exactly this name. */
    public ItemDefinition getDefinition(String name) {
        return definitionsByName.get(SpawnerConfigName.normalize(name));
    }

    /**
     * The spawner a stored or typed name stands for: its own name, one of its {@code aliases}, or a
     * 1.8 {@code <item>_spawner} name. Null when nothing matches.
     */
    public ItemDefinition resolve(String name) {
        String normalized = SpawnerConfigName.normalize(name);
        if (normalized.isEmpty()) return null;
        ItemDefinition definition = definitionsByName.get(normalized);
        if (definition != null) return definition;
        String aliased = aliasToName.get(normalized);
        if (aliased != null) return definitionsByName.get(aliased);
        String legacy = SpawnerFileLayout.legacyBaseId(normalized);
        return legacy == null ? null : definitionsByName.get(legacy);
    }

    /** The base spawner of an item, used for unknown names. */
    public ItemDefinition getBaseDefinition(Material material) {
        return material == null ? null : baseDefinitions.get(material);
    }

    /** The spawner a stored name stands for, or the item's base spawner when the name is gone. */
    public ItemDefinition resolveOrBase(String name, Material material) {
        ItemDefinition definition = resolve(name);
        return definition != null && definition.material() == material ? definition : getBaseDefinition(material);
    }

    public Set<String> getDefinitionNames() {
        return Collections.unmodifiableSet(definitionsByName.keySet());
    }

    /** Where a spawner lives in the file, for the loot editor: {@code diamond} or {@code custom_spawners.x}. */
    public String getSectionPath(String name) {
        return sectionPaths.get(SpawnerConfigName.normalize(name));
    }

    /** Loot of the item's base spawner. */
    public EntityLootConfig getLootConfig(Material material) {
        ItemDefinition definition = getBaseDefinition(material);
        return definition == null ? null : definition.lootConfig();
    }

    /** Head of the item's base spawner, never null. */
    public SpawnerHead getBaseHead(Material material) {
        ItemDefinition definition = getBaseDefinition(material);
        return definition != null ? definition.head() : new SpawnerHead(material, null);
    }

    /** The item rendered inside the spawner cage: {@code nbt_data}, else the first loot template. */
    public ItemStack getDisplayItem(String configName, Material fallback) {
        ItemDefinition definition = resolveOrBase(configName, fallback);
        if (definition == null) {
            return new ItemStack(fallback, 1);
        }
        if (definition.displayItem() != null) {
            return definition.displayItem().clone();
        }
        for (LootItem lootItem : definition.lootConfig().getAllItems()) {
            if (lootItem.template() != null) return lootItem.template().asQuantity(1);
        }
        return new ItemStack(definition.material(), 1);
    }

    /**
     * One spawner from the file.
     *
     * @param base        true for the item's own entry, false for one under {@code custom_spawners}
     * @param displayName replaces the item name everywhere players see this spawner, or null
     * @param displayItem item shown in the spawner cage from {@code nbt_data}, or null
     */
    public record ItemDefinition(String name, Material material, boolean base, String displayName,
                                 EntityLootConfig lootConfig, SpawnerHead head, ItemStack displayItem,
                                 List<String> aliases) {}
}
