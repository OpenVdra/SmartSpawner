package github.nighter.smartspawner.language.file;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Extracting and reading the files of a {@code language/<locale>/} folder. */
public final class LanguageFiles {
    public static final List<String> SUPPORTED_LANGUAGES = Arrays.asList(
            "en_US",
            "tr_TR",
            "vi_VN",
            "es_ES",
            "zh_CN"
    );
    public static final String BASE_LOCALE = "en_US";

    private final JavaPlugin plugin;

    public LanguageFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The active locale plus every locale folder already on disk, bundled or custom. A fresh install
     * only gets the active one; the others are extracted the first time they are selected, or all at
     * once when {@code allBundled} is set because players' own languages are in use.
     */
    public static Set<String> localesInUse(File dataFolder, String activeLocale, boolean allBundled) {
        Set<String> locales = new LinkedHashSet<>();
        locales.add(activeLocale);
        if (allBundled) locales.addAll(SUPPORTED_LANGUAGES);
        File[] folders = new File(dataFolder, "language").listFiles(File::isDirectory);
        if (folders != null) {
            Arrays.stream(folders).map(File::getName).filter(name -> !name.contains(".")).sorted().forEach(locales::add);
        }
        return locales;
    }

    /** Bundled locale names plus every folder under {@code language/}. */
    public Set<String> availableLocales() {
        return localesInUse(plugin.getDataFolder(), BASE_LOCALE, true);
    }

    /** Writes the bundled files of each bundled locale in {@code locales} that are not on disk yet. */
    public void saveDefaultFiles(Collection<String> locales) {
        for (String locale : locales) {
            if (!SUPPORTED_LANGUAGES.contains(locale)) continue;
            for (LanguageFileType fileType : LanguageFileType.values()) {
                String resourcePath = "language/" + locale + "/" + fileType.getFileName();
                File target = new File(plugin.getDataFolder(), resourcePath);
                if (target.exists() || !hasResource(resourcePath)) continue;
                target.getParentFile().mkdirs();
                plugin.saveResource(resourcePath, false);
            }
        }
    }

    /**
     * The locale's files from its folder, or from the jar for a bundled locale that is not on disk.
     * Returns {@code null} for a locale that exists in neither place.
     */
    public LocaleData load(String locale, LocaleData fallback) {
        File localeDir = new File(plugin.getDataFolder(), "language/" + locale);
        if (localeDir.isDirectory()) {
            return new LocaleData(locale,
                    loadFile(localeDir, LanguageFileType.MESSAGES),
                    loadFile(localeDir, LanguageFileType.GUI),
                    loadFile(localeDir, LanguageFileType.ITEMS),
                    loadFile(localeDir, LanguageFileType.FORMATTING),
                    fallback);
        }
        return SUPPORTED_LANGUAGES.contains(locale) ? loadBundled(locale, fallback) : null;
    }

    /** The locale's files exactly as shipped in the jar. */
    public LocaleData loadBundled(String locale, LocaleData fallback) {
        return new LocaleData(locale,
                loadResource(locale, LanguageFileType.MESSAGES),
                loadResource(locale, LanguageFileType.GUI),
                loadResource(locale, LanguageFileType.ITEMS),
                loadResource(locale, LanguageFileType.FORMATTING),
                fallback);
    }

    private YamlConfiguration loadFile(File localeDir, LanguageFileType type) {
        File file = new File(localeDir, type.getFileName());
        if (!file.isFile()) return new YamlConfiguration();
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read language/" + localeDir.getName() + "/" + type.getFileName()
                    + ", so its texts fall back to the next language: " + e.getMessage());
        }
        return config;
    }

    private YamlConfiguration loadResource(String locale, LanguageFileType type) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("language/" + locale + "/" + type.getFileName())) {
            if (stream != null) {
                config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to read the bundled language/" + locale + "/" + type.getFileName()
                    + ": " + e.getMessage());
        }
        return config;
    }

    private boolean hasResource(String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            return stream != null;
        } catch (Exception e) {
            return false;
        }
    }
}
