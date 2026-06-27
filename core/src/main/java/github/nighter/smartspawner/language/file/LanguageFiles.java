package github.nighter.smartspawner.language.file;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public final class LanguageFiles {
    public static final List<String> SUPPORTED_LANGUAGES = Arrays.asList(
            "en_US",
            "en_US_DonutSMP",
            "en_US_DonutSMP_v2",
            "tr_TR",
            "vi_VN"
    );

    private final JavaPlugin plugin;
    private final String defaultLocale;
    private final Set<LanguageFileType> activeFileTypes;

    public LanguageFiles(JavaPlugin plugin, String defaultLocale, Set<LanguageFileType> activeFileTypes) {
        this.plugin = plugin;
        this.defaultLocale = defaultLocale;
        this.activeFileTypes = activeFileTypes;
    }

    public void saveDefaultFiles() {
        for (String locale : SUPPORTED_LANGUAGES) {
            for (LanguageFileType fileType : LanguageFileType.values()) {
                saveResource(String.format("language/%s/%s", locale, fileType.getFileName()));
            }
        }
    }

    public void loadLocale(String locale, Map<String, LocaleData> localeMap) {
        File localeDir = new File(plugin.getDataFolder(), "language/" + locale);
        if (!localeDir.exists() && !localeDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create locale directory for " + locale);
            return;
        }

        YamlConfiguration messages = activeFileTypes.contains(LanguageFileType.MESSAGES)
                ? loadFile(locale, LanguageFileType.MESSAGES) : new YamlConfiguration();
        YamlConfiguration gui = activeFileTypes.contains(LanguageFileType.GUI)
                ? loadFile(locale, LanguageFileType.GUI) : new YamlConfiguration();
        YamlConfiguration commandGui = activeFileTypes.contains(LanguageFileType.COMMAND_GUI)
                ? loadFile(locale, LanguageFileType.COMMAND_GUI) : new YamlConfiguration();
        YamlConfiguration formatting = activeFileTypes.contains(LanguageFileType.FORMATTING)
                ? loadFile(locale, LanguageFileType.FORMATTING) : new YamlConfiguration();
        YamlConfiguration items = activeFileTypes.contains(LanguageFileType.ITEMS)
                ? loadFile(locale, LanguageFileType.ITEMS) : new YamlConfiguration();
        YamlConfiguration commandMessages = activeFileTypes.contains(LanguageFileType.COMMAND_MESSAGES)
                ? loadFile(locale, LanguageFileType.COMMAND_MESSAGES) : new YamlConfiguration();
        YamlConfiguration hologram = activeFileTypes.contains(LanguageFileType.HOLOGRAM)
                ? loadFile(locale, LanguageFileType.HOLOGRAM) : new YamlConfiguration();

        localeMap.put(locale, new LocaleData(messages, gui, commandGui, formatting, items, commandMessages, hologram));
    }

    public YamlConfiguration loadOrCreateFile(String locale, LanguageFileType fileType, boolean forceReload) {
        String fileName = fileType.getFileName();
        File file = new File(plugin.getDataFolder(), "language/" + locale + "/" + fileName);
        YamlConfiguration defaultConfig = new YamlConfiguration();
        YamlConfiguration userConfig = new YamlConfiguration();

        boolean defaultResourceExists = plugin.getResource("language/" + defaultLocale + "/" + fileName) != null;

        if (defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    defaultConfig.loadFromString(new String(inputStream.readAllBytes()));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load default " + fileName);
            }
        }

        if (!file.exists() && defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    file.getParentFile().mkdirs();
                    Files.copy(inputStream, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + fileName + " for locale " + locale);
                return new YamlConfiguration();
            }
        }

        if (file.exists()) {
            try {
                userConfig = YamlConfiguration.loadConfiguration(file);

                boolean updated = false;
                for (String key : defaultConfig.getKeys(false)) {
                    if (!userConfig.contains(key)) {
                        userConfig.set(key, defaultConfig.get(key));
                        updated = true;
                    }
                }

                if (updated) {
                    userConfig.save(file);
                    plugin.getLogger().info("Updated " + fileName + " for locale " + locale);
                }
                return userConfig;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load " + fileName + " for locale " + locale + ". Using defaults.");
                return defaultConfig;
            }
        }
        return new YamlConfiguration();
    }

    public YamlConfiguration loadFile(String locale, LanguageFileType fileType) {
        return loadOrCreateFile(locale, fileType, false);
    }

    private void saveResource(String resourcePath) {
        File resourceFile = new File(plugin.getDataFolder(), resourcePath);
        if (!resourceFile.exists()) {
            try (InputStream inputStream = plugin.getResource(resourcePath)) {
                if (inputStream == null) {
                    plugin.getLogger().warning("Language resource not found in JAR: " + resourcePath);
                    return;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to check language resource: " + resourcePath);
                return;
            }
            resourceFile.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }
}
