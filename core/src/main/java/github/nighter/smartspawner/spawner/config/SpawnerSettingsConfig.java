package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

/**
 * Loads {@code spawner_mobs.yml}: one base spawner per mob, keyed by the mob itself, plus any number
 * of custom spawners under {@code custom_spawners}. See {@link SpawnerFileLayout} for the shape.
 *
 * <p>A custom spawner inherits its head, {@code drop_chance}, {@code nbt_data} and {@code experience}
 * from the base spawner of the same mob when it leaves them out. Its loot is never inherited.</p>
 *
 * <p>The file is kept in sync by the version-less {@link YamlMigrator}: it is created if missing and,
 * on every startup, any keys added by a plugin update are topped up while the user's own edits are
 * preserved.</p>
 */
public class SpawnerSettingsConfig {
    private static final String RESOURCE = "spawner_mobs.yml";
    /** Replaced by {@link #RESOURCE} in 1.8.0. Never read, only reported once. */
    private static final String LEGACY_RESOURCE = "spawners_settings.yml";
    private static final String TYPE_KEY = "entity";

    /**
     * Shown when a mob names no head of its own, or names one that does not exist. Not a config key:
     * it is only ever a fallback, so a server owner has nothing useful to change here.
     */
    private static final Material FALLBACK_HEAD = Material.SPAWNER;

    private final SmartSpawner plugin;
    private final File configFile;

    private final Map<String, MobDefinition> definitionsByName = new LinkedHashMap<>();
    private final Map<String, String> aliasToName = new HashMap<>();
    private final Map<EntityType, MobDefinition> baseDefinitions = new EnumMap<>(EntityType.class);
    private final Map<String, String> sectionPaths = new HashMap<>();

    public SpawnerSettingsConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), RESOURCE);
    }

    /**
     * Load or create the spawners settings configuration.
     */
    public void load() {
        boolean firstRun = !configFile.exists();

        if (!firstRun && SpawnerFileLayout.isLegacy(YamlConfiguration.loadConfiguration(configFile), TYPE_KEY)) {
            SpawnerFileLayout.backup(configFile, plugin.getLogger());
        }

        // Creates the file if missing, converts a 1.8 file, then tops up any keys added by a plugin
        // update. A mob's loot section is left alone once the user has one: those entries are a list
        // they curate, so topping it up would resurrect drops they deleted and duplicate any entry the
        // shipped file has since renamed. custom_spawners belongs to the user entirely.
        YamlMigrator.migrate(configFile, plugin.getResource(RESOURCE), List.of(),
                (user, defaults) -> SpawnerFileLayout.convertLegacy(user, TYPE_KEY, SpawnerSettingsConfig::baseIdOf),
                true,
                YamlMigrator.OwnedSection.curated((defaults, path) -> path.endsWith(".loot")
                        || path.equals(SpawnerFileLayout.CUSTOM_SECTION)),
                plugin.getLogger());

        if (firstRun) {
            SupersededConfigNotice.warn(plugin, RESOURCE, LEGACY_RESOURCE);
        }

        parseConfig(YamlConfiguration.loadConfiguration(configFile));
    }

    public void reload() {
        load();
    }

    private static EntityType entityTypeOf(String raw) {
        String name = SpawnerConfigName.normalize(raw).toUpperCase(Locale.ROOT);
        if (name.isEmpty()) return null;
        try {
            EntityType type = EntityType.valueOf(name);
            return type == EntityType.UNKNOWN ? null : type;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String baseIdOf(String raw) {
        EntityType type = entityTypeOf(raw);
        return type == null ? null : baseId(type);
    }

    private static String baseId(EntityType type) {
        return type.name().toLowerCase(Locale.ROOT);
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

            EntityType type = entityTypeOf(key);
            if (type == null) {
                plugin.getLogger().warning("'" + key + "' in " + RESOURCE + " is not a mob name. Custom spawners go under "
                        + SpawnerFileLayout.CUSTOM_SECTION + ".");
                continue;
            }
            String id = baseId(type);
            if (definitionsByName.containsKey(id)) {
                plugin.getLogger().warning("'" + key + "' in " + RESOURCE + " repeats the " + id + " spawner and is ignored.");
                continue;
            }
            register(parseEntry(id, type, section, true, null), key);
        }

        ConfigurationSection customs = config.getConfigurationSection(SpawnerFileLayout.CUSTOM_SECTION);
        if (customs != null) {
            for (String key : customs.getKeys(false)) {
                ConfigurationSection section = customs.getConfigurationSection(key);
                if (section == null) continue;
                String id = SpawnerConfigName.normalize(key);
                String label = "custom spawner '" + key + "' in " + RESOURCE;
                if (id.isEmpty() || entityTypeOf(id) != null) {
                    plugin.getLogger().warning(label + " uses a mob name. Give it a name of its own.");
                    continue;
                }
                if (definitionsByName.containsKey(id)) {
                    plugin.getLogger().warning(label + " repeats another spawner's name and is ignored.");
                    continue;
                }
                EntityType type = entityTypeOf(section.getString(TYPE_KEY, ""));
                if (type == null) {
                    plugin.getLogger().warning(label + " needs a valid '" + TYPE_KEY + "' mob.");
                    continue;
                }
                register(parseEntry(id, type, section, false, baseDefinitions.get(type)),
                        SpawnerFileLayout.CUSTOM_SECTION + "." + key);
            }
        }

        for (MobDefinition definition : definitionsByName.values()) {
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

    private void register(MobDefinition definition, String sectionPath) {
        definitionsByName.put(definition.name(), definition);
        sectionPaths.put(definition.name(), sectionPath);
        if (definition.base()) {
            baseDefinitions.put(definition.entityType(), definition);
        }
    }

    private MobDefinition parseEntry(String id, EntityType type, ConfigurationSection section, boolean base,
                                     MobDefinition inherited) {
        String label = id + " in " + RESOURCE;

        int experience = section.contains("experience") || inherited == null
                ? section.getInt("experience", 0)
                : inherited.lootConfig().experience();
        EntityLootConfig lootConfig = new EntityLootConfig(experience, parseLoot(section, id));

        SpawnerHead head = SpawnerHead.parse(section, FALLBACK_HEAD, label, plugin.getLogger());
        if (head == null && inherited != null) head = inherited.head();

        Double dropChance = inherited == null ? null : inherited.dropChance();
        if (section.contains("drop_chance")) {
            dropChance = section.getDouble("drop_chance", 100.0);
            if (dropChance < 0.0 || dropChance > 100.0) {
                plugin.getLogger().warning("Invalid drop_chance for " + label + ". It must be 0.0 to 100.0; using 100.0");
                dropChance = 100.0;
            }
        }

        EntitySnapshot snapshot = section.contains("nbt_data") || inherited == null
                ? parseEntityDisplay(type, section, label)
                : inherited.snapshot();

        String displayName = section.getString("display_name");
        if (displayName != null && displayName.isBlank()) displayName = null;

        return new MobDefinition(id, type, base, displayName, lootConfig, head, dropChance, snapshot,
                List.copyOf(section.getStringList(SpawnerFileLayout.ALIASES)));
    }

    private List<LootItem> parseLoot(ConfigurationSection section, String id) {
        List<LootItem> items = new ArrayList<>();
        ConfigurationSection lootSection = section.getConfigurationSection("loot");
        if (lootSection == null) {
            return items;
        }
        for (String itemKey : lootSection.getKeys(false)) {
            ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
            if (itemSection == null) continue;
            LootItem lootItem = LootEntryParser.parse(itemSection, itemKey, plugin.getItemPriceManager(),
                    plugin.getLogger(), "spawner " + id);
            if (lootItem != null) {
                items.add(lootItem);
            }
        }
        return items;
    }

    private EntitySnapshot parseEntityDisplay(EntityType entityType, ConfigurationSection section, String label) {
        String nbt = section.getString("nbt_data");
        if (nbt == null || nbt.isBlank()) {
            return null;
        }

        String trimmed = nbt.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
            plugin.getLogger().warning("Invalid nbt_data for " + label + ": expected an SNBT compound");
            return null;
        }

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        String fullNbt = "{id:\"" + entityType.getKey().asString() + "\""
                + (body.isEmpty() ? "" : "," + body) + "}";
        try {
            EntitySnapshot snapshot = plugin.getServer().getEntityFactory().createEntitySnapshot(fullNbt);
            if (snapshot.getEntityType() == entityType) {
                return snapshot;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid nbt_data for " + label + ": " + e.getMessage());
        }
        return null;
    }

    // ===== Lookups =====

    /** The spawner with exactly this name. */
    public MobDefinition getDefinition(String name) {
        return definitionsByName.get(SpawnerConfigName.normalize(name));
    }

    /**
     * The spawner a stored or typed name stands for: its own name, one of its {@code aliases}, or a
     * 1.8 {@code <mob>_spawner} name. Null when nothing matches.
     */
    public MobDefinition resolve(String name) {
        String normalized = SpawnerConfigName.normalize(name);
        if (normalized.isEmpty()) return null;
        MobDefinition definition = definitionsByName.get(normalized);
        if (definition != null) return definition;
        String aliased = aliasToName.get(normalized);
        if (aliased != null) return definitionsByName.get(aliased);
        String legacy = SpawnerFileLayout.legacyBaseId(normalized);
        return legacy == null ? null : definitionsByName.get(legacy);
    }

    /** The base spawner of a mob, used for natural spawners, spawn eggs and unknown names. */
    public MobDefinition getBaseDefinition(EntityType type) {
        return type == null ? null : baseDefinitions.get(type);
    }

    /** The spawner a stored name stands for, or the mob's base spawner when the name is gone. */
    public MobDefinition resolveOrBase(String name, EntityType type) {
        MobDefinition definition = resolve(name);
        return definition != null && definition.entityType() == type ? definition : getBaseDefinition(type);
    }

    public Set<String> getDefinitionNames() {
        return Collections.unmodifiableSet(definitionsByName.keySet());
    }

    /** Where a spawner lives in the file, for the loot editor: {@code zombie} or {@code custom_spawners.x}. */
    public String getSectionPath(String name) {
        return sectionPaths.get(SpawnerConfigName.normalize(name));
    }

    /** Loot of the mob's base spawner. */
    public EntityLootConfig getLootConfig(EntityType entityType) {
        MobDefinition definition = getBaseDefinition(entityType);
        return definition == null ? null : definition.lootConfig();
    }

    /** Head of the mob's base spawner, never null. */
    public SpawnerHead getBaseHead(EntityType entityType) {
        MobDefinition definition = getBaseDefinition(entityType);
        return definition != null && definition.head() != null ? definition.head() : new SpawnerHead(FALLBACK_HEAD, null);
    }

    public double getSpawnerDropChance(String configName, EntityType fallback) {
        MobDefinition definition = resolveOrBase(configName, fallback);
        return definition == null || definition.dropChance() == null ? 100.0 : definition.dropChance();
    }

    public boolean hasSpawnerDropChance(String configName, EntityType fallback) {
        MobDefinition definition = resolveOrBase(configName, fallback);
        return definition != null && definition.dropChance() != null;
    }

    public EntitySnapshot getEntityDisplaySnapshot(String configName, EntityType fallback) {
        MobDefinition definition = resolveOrBase(configName, fallback);
        return definition == null ? null : definition.snapshot();
    }

    /**
     * One spawner from the file.
     *
     * @param base        true for the mob's own entry, false for one under {@code custom_spawners}
     * @param displayName replaces the mob name everywhere players see this spawner, or null
     * @param head        menu icon, or null for the default spawner block
     * @param dropChance  chance to drop the spawner item when broken, or null when not configured
     */
    public record MobDefinition(String name, EntityType entityType, boolean base, String displayName,
                                EntityLootConfig lootConfig, SpawnerHead head, Double dropChance,
                                EntitySnapshot snapshot, List<String> aliases) {}
}
