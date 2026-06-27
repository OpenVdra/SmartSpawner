package github.nighter.smartspawner.config;

import github.nighter.smartspawner.SmartSpawner;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Getter
public class Config {
    @Getter(AccessLevel.NONE)
    private static volatile Config instance;

    // Special key inside natural_spawner.drop_chance that applies to every spawner type
    private static final String DEFAULT_DROP_CHANCE_KEY = "default";

    // Performance settings
    private final boolean approximateLoot;
    private final int approximationThreshold;

    // Spawner breaking settings
    private final boolean breakEnabled;
    private final boolean directToInventory;
    private final int durabilityLoss;
    private final boolean sneakBreakEnabled;
    private final boolean silkTouchRequired;
    private final int silkTouchLevel;
    private final boolean sellAndXpBreak;

    // Spawner property settings
    private final boolean allowExpMending;

    // Natural spawner settings
    private final boolean naturalBreakable;
    private final boolean convertNaturalToSmartSpawner;

    // Particle settings
    private final boolean spawnerStackParticlesEnabled;
    private final boolean spawnerActivateParticlesEnabled;
    private final boolean spawnerGenerateLootParticlesEnabled;

    // Parsed lookup data
    @Getter(AccessLevel.NONE)
    private final Set<Material> requiredTools;
    @Getter(AccessLevel.NONE)
    private final Map<EntityType, Double> naturalSpawnerDropChances;
    @Getter(AccessLevel.NONE)
    private final double naturalSpawnerDefaultDropChance;

    private Config(FileConfiguration config, Logger logger) {
        // Performance settings
        this.approximateLoot = config.getBoolean("performance.loot_generation.approximate_loot", true);
        this.approximationThreshold = config.getInt("performance.loot_generation.approximation_threshold", 1000);

        // Spawner breaking settings
        this.breakEnabled = config.getBoolean("spawner_break.enabled", true);
        this.directToInventory = config.getBoolean("spawner_break.direct_to_inventory", false);
        this.durabilityLoss = config.getInt("spawner_break.durability_loss", 1);
        this.sneakBreakEnabled = config.getBoolean("spawner_break.sneak_break", true);
        this.silkTouchRequired = config.getBoolean("spawner_break.silk_touch.required", true);
        this.silkTouchLevel = config.getInt("spawner_break.silk_touch.level", 1);
        this.sellAndXpBreak = config.getBoolean("spawner_break.sell_and_xp_break", true);

        // Spawner property settings
        this.allowExpMending = config.getBoolean(
                "spawner_properties.default.allow_exp_mending", true);

        // Natural spawner settings
        this.naturalBreakable = config.getBoolean("natural_spawner.breakable", false);
        this.convertNaturalToSmartSpawner = config.getBoolean("natural_spawner.convert_to_smart_spawner", false);

        // Particle settings
        this.spawnerStackParticlesEnabled = config.getBoolean("particle.spawner_stack", true);
        this.spawnerActivateParticlesEnabled = config.getBoolean("particle.spawner_activate", true);
        this.spawnerGenerateLootParticlesEnabled = config.getBoolean("particle.spawner_generate_loot", true);

        // Parsed lookup data
        this.requiredTools = loadRequiredTools(config, logger);
        DropChanceResult dropChanceResult = loadNaturalSpawnerDropChances(config, logger);
        this.naturalSpawnerDefaultDropChance = dropChanceResult.defaultChance();
        this.naturalSpawnerDropChances = dropChanceResult.entityChances();
    }

    public static Config get() {
        return instance;
    }

    public static void reload(SmartSpawner plugin) {
        load(plugin);
    }

    public static void load(SmartSpawner plugin) {
        instance = new Config(plugin.getConfig(), plugin.getLogger());
    }

    public boolean isRequiredTool(Material material) {
        return requiredTools.contains(material);
    }

    public double getNaturalSpawnerDropChance(EntityType entityType) {
        return naturalSpawnerDropChances.getOrDefault(entityType, naturalSpawnerDefaultDropChance);
    }

    private static Set<Material> loadRequiredTools(FileConfiguration config, Logger logger) {
        return config.getStringList("spawner_break.required_tools")
                .stream()
                .map(toolName -> {
                    try {
                        return Material.valueOf(toolName.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Invalid material in spawner_break.required_tools: " + toolName);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record DropChanceResult(double defaultChance, Map<EntityType, Double> entityChances) {}

    private static DropChanceResult loadNaturalSpawnerDropChances(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("natural_spawner.drop_chance");
        double defaultDropChance = 100.0;
        Map<EntityType, Double> loadedDropChances = new EnumMap<>(EntityType.class);

        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.equalsIgnoreCase(DEFAULT_DROP_CHANCE_KEY)) {
                    double chance = section.getDouble(key, 100.0);
                    if (chance < 0.0 || chance > 100.0) {
                        logger.warning("Invalid drop chance for natural_spawner.drop_chance." + key +
                                ". Value must be between 0.0 and 100.0; using 100.0");
                        chance = 100.0;
                    }
                    defaultDropChance = chance;
                } else {
                    EntityType entityType;
                    try {
                        entityType = EntityType.valueOf(key.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Invalid entity in natural_spawner.drop_chance: " + key);
                        continue;
                    }

                    double dropChance = section.getDouble(key, 100.0);
                    if (dropChance < 0.0 || dropChance > 100.0) {
                        logger.warning("Invalid drop chance for natural_spawner.drop_chance." + key +
                                ". Value must be between 0.0 and 100.0; using 100.0");
                        dropChance = 100.0;
                    }

                    loadedDropChances.put(entityType, dropChance);
                }
            }
        }

        return new DropChanceResult(defaultDropChance, Map.copyOf(loadedDropChances));
    }
}
