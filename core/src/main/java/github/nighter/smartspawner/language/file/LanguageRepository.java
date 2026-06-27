package github.nighter.smartspawner.language.file;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LanguageRepository {
    private final JavaPlugin plugin;
    private final Set<LanguageFileType> activeFileTypes;
    private final Map<String, LocaleData> locales = new HashMap<>();
    private final Set<String> loadedLocales = new HashSet<>();
    private String defaultLocale;
    private LocaleData defaultLocaleData;

    public LanguageRepository(JavaPlugin plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        this.activeFileTypes = fileTypes.length == 0
                ? EnumSet.noneOf(LanguageFileType.class)
                : EnumSet.copyOf(Arrays.asList(fileTypes));
        this.defaultLocale = configuredLocale();
    }

    public void initialize() {
        loadLanguages();
        files().saveDefaultFiles();
        cacheDefaultLocale();
    }

    public void loadLanguages(LanguageFileType... ignoredFileTypes) {
        File languageDirectory = new File(plugin.getDataFolder(), "language");
        if (!languageDirectory.exists() && !languageDirectory.mkdirs()) {
            plugin.getLogger().severe("Failed to create language directory!");
            return;
        }

        locales.remove(defaultLocale);
        files().loadLocale(defaultLocale, locales);
        loadedLocales.add(defaultLocale);
        cacheDefaultLocale();
    }

    public void reload() {
        defaultLocale = configuredLocale();
        loadedLocales.add(defaultLocale);

        for (String locale : Set.copyOf(loadedLocales)) {
            locales.remove(locale);
            new LanguageFiles(plugin, defaultLocale, activeFileTypes).loadLocale(locale, locales);
        }

        cacheDefaultLocale();
        plugin.getLogger().info("Successfully reloaded language files for language " + defaultLocale);
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public LocaleData current() {
        return defaultLocaleData;
    }

    public boolean isActive(LanguageFileType fileType) {
        return activeFileTypes.contains(fileType);
    }

    private LanguageFiles files() {
        return new LanguageFiles(plugin, defaultLocale, activeFileTypes);
    }

    private String configuredLocale() {
        return plugin.getConfig().getString("language", "en_US");
    }

    private void cacheDefaultLocale() {
        defaultLocaleData = locales.get(defaultLocale);
        if (defaultLocaleData != null) {
            return;
        }

        plugin.getLogger().severe("Failed to cache default locale data for " + defaultLocale);
        defaultLocaleData = emptyLocale();
        locales.put(defaultLocale, defaultLocaleData);
    }

    private LocaleData emptyLocale() {
        return new LocaleData(
                new YamlConfiguration(),
                new YamlConfiguration(),
                new YamlConfiguration(),
                new YamlConfiguration(),
                new YamlConfiguration(),
                new YamlConfiguration(),
                new YamlConfiguration()
        );
    }
}
