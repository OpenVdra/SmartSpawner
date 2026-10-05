package github.nighter.smartspawner.spawner.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The shape shared by {@code spawner_mobs.yml} and {@code spawner_items.yml}, and the one-time
 * conversion from the 1.8 shape.
 *
 * <pre>
 * zombie:              # base spawner, the key is the mob (or item) itself
 *   loot: ...
 * custom_spawners:
 *   lucky_zombie:      # custom spawner, names its mob with a type key
 *     entity: ZOMBIE
 *     loot: ...
 * </pre>
 *
 * <p>In 1.8 every entry was top level with its own type key ({@code zombie_spawner: entity: ZOMBIE}).
 * Spawners placed in that version stored the entry key, so the conversion keeps every old name
 * reachable: {@code <type>_spawner} is read as the base spawner by {@link #legacyBaseId}, and any other
 * entry promoted to base keeps its old key in {@code aliases}.</p>
 */
public final class SpawnerFileLayout {
    public static final String CUSTOM_SECTION = "custom_spawners";
    public static final String ALIASES = "aliases";
    private static final String LEGACY_SUFFIX = "_spawner";

    private SpawnerFileLayout() {}

    /** The base id an old {@code <type>_spawner} name stands for, or null when it is not one. */
    public static String legacyBaseId(String normalizedName) {
        return normalizedName.endsWith(LEGACY_SUFFIX) && normalizedName.length() > LEGACY_SUFFIX.length()
                ? normalizedName.substring(0, normalizedName.length() - LEGACY_SUFFIX.length())
                : null;
    }

    /** True for a file still in the 1.8 shape: no custom section, and entries that name their own type. */
    public static boolean isLegacy(ConfigurationSection config, String typeKey) {
        if (config.isConfigurationSection(CUSTOM_SECTION)) {
            return false;
        }
        for (String key : config.getKeys(false)) {
            ConfigurationSection entry = config.getConfigurationSection(key);
            if (entry != null && entry.contains(typeKey)) {
                return true;
            }
        }
        return false;
    }

    /** Copies the file aside before its first conversion, so the operator can always compare. */
    public static void backup(File file, Logger logger) {
        File backup = new File(file.getParentFile(), file.getName() + ".1.8-backup");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Converting " + file.getName() + " to the new layout. The old file was saved as "
                    + backup.getName() + ".");
        } catch (IOException e) {
            logger.warning("Could not back up " + file.getName() + " before converting it: " + e.getMessage());
        }
    }

    /**
     * Rewrites a 1.8 file in place. For each type, the entry named {@code <type>_spawner} (or else the
     * first one) becomes the base spawner under the type's own key; every other entry moves to
     * {@code custom_spawners} unchanged. Comments travel with their keys.
     *
     * @param typeKey  {@code entity} or {@code item}
     * @param baseIdOf the base id for a raw type name ({@code ZOMBIE} to {@code zombie}), null when invalid
     */
    public static boolean convertLegacy(YamlConfiguration config, String typeKey, Function<String, String> baseIdOf) {
        if (!isLegacy(config, typeKey)) {
            return false;
        }

        List<String> keys = new ArrayList<>(config.getKeys(false));
        Map<String, String> baseIdByKey = new LinkedHashMap<>();
        Map<String, String> baseKeyById = new LinkedHashMap<>();
        for (String key : keys) {
            ConfigurationSection entry = config.getConfigurationSection(key);
            if (entry == null) continue;
            String baseId = baseIdOf.apply(entry.getString(typeKey, key));
            if (baseId == null) continue;
            baseIdByKey.put(key, baseId);
            String name = SpawnerConfigName.normalize(key);
            String current = baseKeyById.get(baseId);
            boolean preferred = name.equals(baseId + LEGACY_SUFFIX) || name.equals(baseId);
            if (current == null || (preferred && !isPreferred(current, baseId))) {
                baseKeyById.put(baseId, key);
            }
        }

        MemoryConfiguration out = new MemoryConfiguration();
        List<String> customs = new ArrayList<>();
        for (String key : keys) {
            String baseId = baseIdByKey.get(key);
            if (!config.isConfigurationSection(key)) {
                copy(config, key, out, key, Set.of());
            } else if (baseId != null && key.equals(baseKeyById.get(baseId))) {
                copy(config, key, out, baseId, Set.of(typeKey));
                if (!isPreferred(key, baseId)) {
                    out.set(baseId + "." + ALIASES, List.of(SpawnerConfigName.normalize(key)));
                }
            } else {
                customs.add(key);
            }
        }
        out.createSection(CUSTOM_SECTION);
        for (String key : customs) {
            copy(config, key, out, CUSTOM_SECTION + "." + key, Set.of());
        }

        for (String key : keys) {
            config.set(key, null);
        }
        for (String key : out.getKeys(false)) {
            copy(out, key, config, key, Set.of());
        }
        return true;
    }

    private static boolean isPreferred(String key, String baseId) {
        String name = SpawnerConfigName.normalize(key);
        return name.equals(baseId + LEGACY_SUFFIX) || name.equals(baseId);
    }

    /** Deep copy of one key, with the comments and inline comments of every node under it. */
    private static void copy(ConfigurationSection from, String fromPath, ConfigurationSection to, String toPath,
                             Set<String> skipChildren) {
        if (from.isConfigurationSection(fromPath)) {
            to.createSection(toPath);
            ConfigurationSection section = from.getConfigurationSection(fromPath);
            for (String child : section.getKeys(false)) {
                if (skipChildren.contains(child)) continue;
                copy(from, fromPath + "." + child, to, toPath + "." + child, Set.of());
            }
        } else {
            to.set(toPath, from.get(fromPath));
        }
        to.setComments(toPath, from.getComments(fromPath));
        to.setInlineComments(toPath, from.getInlineComments(fromPath));
    }
}
