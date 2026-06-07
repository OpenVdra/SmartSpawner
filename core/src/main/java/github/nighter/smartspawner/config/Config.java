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

        // Natural spawner settings
        this.naturalBreakable = config.getBoolean("natural_spawner.breakable", false);
        this.convertNaturalToSmartSpawner = config.getBoolean("natural_spawner.convert_to_smart_spawner", false);

        // Particle settings
        this.spawnerStackParticlesEnabled = config.getBoolean("particle.spawner_stack", true);
        this.spawnerActivateParticlesEnabled = config.getBoolean("particle.spawner_activate", true);
        this.spawnerGenerateLootParticlesEnabled = config.getBoolean("particle.spawner_generate_loot", true);

        // Parsed lookup data
        this.requiredTools = loadRequiredTools(config, logger);
        this.naturalSpawnerDropChances = loadNaturalSpawnerDropChances(config, logger);
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
        return naturalSpawnerDropChances.getOrDefault(entityType, 100.0);
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

    private static Map<EntityType, Double> loadNaturalSpawnerDropChances(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("natural_spawner.drop_chance");
        if (section == null) {
            return Map.of();
        }

        Map<EntityType, Double> loadedDropChances = new EnumMap<>(EntityType.class);
        for (String entityName : section.getKeys(false)) {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException ex) {
                logger.warning("Invalid entity in natural_spawner.drop_chance: " + entityName);
                continue;
            }

            double dropChance = section.getDouble(entityName, 100.0);
            if (dropChance < 0.0 || dropChance > 100.0) {
                logger.warning("Invalid drop chance for natural_spawner.drop_chance." + entityName +
                        ". Value must be between 0.0 and 100.0; using 100.0");
                dropChance = 100.0;
            }

            loadedDropChances.put(entityType, dropChance);
        }

        return Map.copyOf(loadedDropChances);
    }
}
