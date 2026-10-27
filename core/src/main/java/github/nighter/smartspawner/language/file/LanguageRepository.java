package github.nighter.smartspawner.language.file;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded locales. The server locale ({@code language} in config.yml) is always loaded and falls
 * back to the bundled {@code en_US}, so a key missing from an owner's file, or from a custom locale,
 * still has text. Players' own locales ({@code language_auto_detect}) are loaded the first time a
 * player with that client language needs one, and fall back to the server locale.
 */
public final class LanguageRepository {
    private final JavaPlugin plugin;
    private final LanguageFiles files;
    private final Map<String, Optional<LocaleData>> playerLocales = new ConcurrentHashMap<>();
    private volatile String serverLocale;
    private volatile boolean autoDetect;
    private volatile LocaleData server;
    private volatile Set<String> available = Set.of();

    public LanguageRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.files = new LanguageFiles(plugin);
    }

    public void initialize() {
        reload();
    }

    public void reload() {
        serverLocale = plugin.getConfig().getString("language", LanguageFiles.BASE_LOCALE);
        autoDetect = plugin.getConfig().getBoolean("language_auto_detect", false);
        // A bundled locale selected for the first time is extracted here.
        files.saveDefaultFiles(LanguageFiles.localesInUse(plugin.getDataFolder(), serverLocale, autoDetect));

        LocaleData base = files.loadBundled(LanguageFiles.BASE_LOCALE, null);
        LocaleData loaded = files.load(serverLocale, base);
        if (loaded == null) {
            plugin.getLogger().severe("Language " + serverLocale + " has no folder under language/ and is not bundled. "
                    + "Using the bundled " + LanguageFiles.BASE_LOCALE + " texts.");
            loaded = base;
        }
        server = loaded;
        available = files.availableLocales();
        playerLocales.clear();
    }

    public String serverLocale() {
        return serverLocale;
    }

    public LocaleData server() {
        return server;
    }

    public boolean autoDetect() {
        return autoDetect;
    }

    /**
     * The locale folder name to use for a client language, or {@code null} for the server locale. An
     * exact match ({@code vi_VN}) wins, then the same language ({@code es_MX} gets {@code es_ES}).
     */
    public String match(Locale clientLocale) {
        if (clientLocale == null) return null;
        String tag = clientLocale.toString();
        String language = clientLocale.getLanguage();
        String sameLanguage = null;
        for (String locale : available) {
            if (locale.equalsIgnoreCase(tag)) return locale;
            if (sameLanguage == null && !language.isEmpty()
                    && locale.toLowerCase(Locale.ROOT).startsWith(language.toLowerCase(Locale.ROOT) + "_")) {
                sameLanguage = locale;
            }
        }
        return sameLanguage;
    }

    /** A player locale's files, falling back to the server locale. {@code null} when it cannot be loaded. */
    public LocaleData forLocale(String locale) {
        if (locale == null || locale.equals(serverLocale)) return server;
        return playerLocales.computeIfAbsent(locale, name -> Optional.ofNullable(files.load(name, server)))
                .orElse(null);
    }

    public Set<String> availableLocales() {
        return available;
    }
}
