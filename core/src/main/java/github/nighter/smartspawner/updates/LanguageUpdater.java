package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.file.LanguageFiles;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LanguageUpdater {
    private static final String VERSION_KEY = "language_version";

    private final SmartSpawner plugin;
    private final Set<LanguageFileType> activeFileTypes = new HashSet<>();

    public LanguageUpdater(SmartSpawner plugin) {
        this(plugin, LanguageFileType.values());
    }

    public LanguageUpdater(SmartSpawner plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        activeFileTypes.addAll(Arrays.asList(fileTypes));
        checkAndUpdateLanguageFiles();
    }

    @Getter
    public enum LanguageFileType {
        MESSAGES("messages.yml"),
        GUI("gui.yml"),
        COMMAND_GUI("command_gui.yml"),
        FORMATTING("formatting.yml"),
        ITEMS("items.yml"),
        COMMAND_MESSAGES("command_messages.yml");

        private final String fileName;
        LanguageFileType(String fileName) { this.fileName = fileName; }
    }

    /**
     * For each supported locale, ensures every language file is present and up-to-date.
     * Files are created if missing, or merged-updated if the stored version is older than
     * the running plugin version. User-customised values are preserved during updates.
     */
    public void checkAndUpdateLanguageFiles() {
        for (String language : LanguageFiles.SUPPORTED_LANGUAGES) {
            File langDir = new File(plugin.getDataFolder(), "language/" + language);
            langDir.mkdirs();

            for (LanguageFileType type : activeFileTypes) {
                File langFile = new File(langDir, type.getFileName());
                String resource = "language/" + language + "/" + type.getFileName();
                if (type == LanguageFileType.ITEMS) {
                    updateItemDefaults(langFile, resource);
                } else {
                    ConfigVersionService.updateFile(plugin, langFile, resource, VERSION_KEY);
                }
            }
        }
    }

    private void updateItemDefaults(File langFile, String resource) {
        if (!langFile.exists()) {
            ConfigVersionService.updateFile(plugin, langFile, resource, VERSION_KEY);
            return;
        }

        FileConfiguration current = YamlConfiguration.loadConfiguration(langFile);
        String currentVersion = plugin.getPluginMeta().getVersion();
        if (new Version(current.getString(VERSION_KEY, "0.0.0"))
                .compareTo(new Version(currentVersion)) >= 0) {
            return;
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) {
                plugin.getLogger().warning("Language resource not found in JAR: " + resource);
                return;
            }
            defaults.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));

            for (String sectionName : new String[]{"smart_spawner", "item_spawner", "vanilla_spawner"}) {
                String defaultPath = sectionName + ".default";
                ConfigurationSection section = defaults.getConfigurationSection(defaultPath);
                if (section == null) {
                    continue;
                }

                for (String key : section.getKeys(true)) {
                    String path = defaultPath + "." + key;
                    if (!section.isConfigurationSection(key) && !current.contains(path)) {
                        current.set(path, defaults.get(path));
                    }
                }
            }

            current.set(VERSION_KEY, currentVersion);
            current.save(langFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update default item language keys in "
                    + langFile.getName() + ": " + e.getMessage());
        }
    }
}
