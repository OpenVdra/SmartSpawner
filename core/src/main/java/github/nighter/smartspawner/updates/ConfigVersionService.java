package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Centralized utilities for versioned plugin config-file management.
 *
 * <p>All config updaters ({@link ConfigUpdater}, {@link LanguageUpdater},
 * {@link GuiLayoutUpdater}, {@code DiscordConfigUpdater}) delegate the shared
 * boilerplate (flatten, diff, backup, write-with-header, apply-user-values) here,
 * eliminating duplication and ensuring consistent behaviour.</p>
 *
 * <p>All methods are static – this class is never instantiated.</p>
 */
public final class ConfigVersionService {

    private ConfigVersionService() {}

    // ── Full update cycle ────────────────────────────────────────────────────

    /**
     * Performs the complete update cycle for a single versioned config file:
     * <ol>
     *   <li>If the file does not exist, extract the resource with a version header and return {@code true}.</li>
     *   <li>Read the file's {@code versionKey} and compare it to the running plugin version.</li>
     *   <li>If outdated: optionally create a backup, write fresh defaults, re-apply user values, save.</li>
     * </ol>
     *
     * @param plugin       Plugin instance used for resource extraction and logging
     * @param dataFile     Target file inside the plugin data folder
     * @param resourcePath Resource path inside the JAR (e.g. {@code "config.yml"})
     * @param versionKey   YAML key that holds the version string (e.g. {@code "config_version"})
     * @return {@code true} if the file was created or updated; {@code false} if already current
     */
    public static boolean updateFile(
            SmartSpawner plugin,
            File dataFile,
            String resourcePath,
            String versionKey
    ) {
        return updateFile(plugin, dataFile, resourcePath, versionKey, null);
    }

    /**
     * Same as {@link #updateFile(SmartSpawner, File, String, String)} but accepts an optional
     * {@link PostUpdateAction} that is called <em>before</em> the user values are re-applied,
     * allowing callers to perform path migrations on the flattened map.
     */
    public static boolean updateFile(
            SmartSpawner plugin,
            File dataFile,
            String resourcePath,
            String versionKey,
            PostUpdateAction postAction
    ) {
        String currentVersion = plugin.getPluginMeta().getVersion();

        if (!dataFile.exists()) {
            writeResourceWithHeader(plugin, resourcePath, dataFile, versionKey, currentVersion);
            return true;
        }

        FileConfiguration current = YamlConfiguration.loadConfiguration(dataFile);
        String fileVersionStr = current.getString(versionKey, "0.0.0");
        Version fileVersion   = new Version(fileVersionStr);
        Version pluginVersion = new Version(currentVersion);

        if (fileVersion.compareTo(pluginVersion) >= 0) {
            return false; // Already up-to-date
        }

        plugin.debug("Updating " + dataFile.getName() + " from v" + fileVersionStr + " → v" + currentVersion);

        try {
            Map<String, Object> userValues = flattenConfig(current);

            // Allow caller to migrate renamed paths before applying back
            if (postAction != null) {
                postAction.migrate(userValues);
            }

            // Write fresh defaults to a temp file
            File tempFile = File.createTempFile("ss_upd_", ".yml", dataFile.getParentFile());
            try {
                writeResourceWithHeader(plugin, resourcePath, tempFile, versionKey, currentVersion);
                FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
                newConfig.set(versionKey, currentVersion);

                if (hasConfigDifferences(userValues, newConfig, versionKey)) {
                    createBackup(plugin, dataFile, fileVersionStr);
                } else {
                    plugin.debug("No significant differences in " + dataFile.getName() + " – skipping backup");
                }

                applyUserValues(newConfig, userValues, versionKey, plugin);
                newConfig.save(dataFile);
            } finally {
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update " + dataFile.getName(), e);
        }

        return true;
    }

    // ── Low-level helpers ────────────────────────────────────────────────────

    /**
     * Extracts a resource from the JAR and writes it to {@code dest} with a version header
     * prepended as the first two lines.
     */
    public static void writeResourceWithHeader(
            SmartSpawner plugin,
            String resourcePath,
            File dest,
            String versionKey,
            String version
    ) {
        try {
            if (dest.getParentFile() != null) {
                dest.getParentFile().mkdirs();
            }
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in == null) {
                    plugin.getLogger().warning("Resource not found in JAR: " + resourcePath);
                    if (!dest.exists()) {
                        dest.createNewFile();
                    }
                    return;
                }
                List<String> body = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();

                List<String> out = new ArrayList<>(body.size() + 3);
                out.add("# Configuration version – do not modify this value");
                out.add(versionKey + ": " + version);
                out.add("");
                out.addAll(body);
                Files.write(dest.toPath(), out, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write " + dest.getName(), e);
        }
    }

    /**
     * Recursively flattens a {@link ConfigurationSection} to a {@code path → leaf-value} map.
     * Only leaf nodes (non-sections) are included.
     */
    public static Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    /**
     * Copies user values back into {@code newConfig}.
     * Skips the {@code versionKey} and any paths that no longer exist in the new defaults
     * (removed keys are intentionally not carried forward).
     */
    public static void applyUserValues(
            FileConfiguration newConfig,
            Map<String, Object> userValues,
            String versionKey,
            SmartSpawner plugin
    ) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            if (path.equals(versionKey)) continue;
            if (newConfig.contains(path)) {
                newConfig.set(path, entry.getValue());
            } else if (isOptionalDropChancePath(path, newConfig)) {
                newConfig.set(path, entry.getValue());
            } else {
                plugin.debug("Path '" + path + "' no longer exists in new defaults – skipping");
            }
        }
    }

    private static boolean isOptionalDropChancePath(String path, FileConfiguration newConfig) {
        return path.startsWith("natural_spawner.drop_chance.")
                && newConfig.isConfigurationSection("natural_spawner");
    }

    /**
     * Returns {@code true} if the user-values map differs from the new defaults enough
     * to warrant a backup (i.e. new/removed/changed keys).
     */
    public static boolean hasConfigDifferences(
            Map<String, Object> userValues,
            FileConfiguration newConfig,
            String versionKey
    ) {
        Map<String, Object> newMap = flattenConfig(newConfig);

        // Keys removed or value-changed
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            if (entry.getKey().equals(versionKey)) continue;
            if (!newConfig.contains(entry.getKey())) return true;
            Object nd = newConfig.get(entry.getKey());
            if (nd != null && !nd.equals(entry.getValue())) return true;
        }

        // New keys added
        for (String path : newMap.keySet()) {
            if (!path.equals(versionKey) && !userValues.containsKey(path)) return true;
        }

        return false;
    }

    /**
     * Creates a versioned backup copy of {@code file} in the same directory.
     * Logs the backup name on success; logs a warning (not exception) on failure.
     */
    public static void createBackup(SmartSpawner plugin, File file, String version) {
        try {
            String backupName = file.getName().replace(".yml", "_backup_" + version + ".yml");
            File backup = new File(file.getParentFile(), backupName);
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Config backup created: " + backup.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create backup of " + file.getName() + ": " + e.getMessage());
        }
    }

    // ── Functional interface for path migration ──────────────────────────────

    /**
     * Callback used by {@link #updateFile} to allow callers to rename/migrate
     * config paths in the flattened user-value map before they are applied back.
     */
    @FunctionalInterface
    public interface PostUpdateAction {
        void migrate(Map<String, Object> userValues);
    }
}
