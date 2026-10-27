package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.format.TextFormatter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Retires the {@code en_US_DonutSMP} and {@code en_US_DonutSMP_v2} locales. They were GUI themes stored
 * as languages: every file but {@code gui.yml} matched {@code en_US}, and the look needed both
 * {@code language} and {@code gui_layout} set to agree. Their GUI texts now ship as
 * {@code gui_layouts/<layout>/texts.yml}, so {@code gui_layout} alone picks the look.
 *
 * <p>For each old folder still on disk, after a backup ({@link LanguageBackup}):</p>
 * <ol>
 *   <li>{@code language: en_US_DonutSMP[_v2]} becomes {@code en_US}, and a {@code gui_layout} still on
 *       {@code default} becomes the matching layout.</li>
 *   <li>Every GUI text the owner changed (it differs from the shipped {@code texts.yml} value, or from
 *       {@code en_US/gui.yml} where the layout has none) is written into the layout's {@code texts.yml}.</li>
 *   <li>If it was the active locale, its other files that the owner edited replace the ones in
 *       {@code language/en_US/}, so messages they customised keep showing.</li>
 *   <li>The folder is deleted.</li>
 * </ol>
 *
 * <p>Detection is the folders' presence, not a version, so once they are gone this does nothing.</p>
 */
public final class LayoutTextsMigration {
    private static final Map<String, String> LEGACY_LOCALES = Map.of(
            "en_US_DonutSMP", "DonutSMP",
            "en_US_DonutSMP_v2", "DonutSMP_v2");
    private static final String BASE_LOCALE = "en_US";
    private static final String DEFAULT_LAYOUT = "default";
    private static final String GUI_FILE = "gui.yml";
    private static final String TEXTS_FILE = "texts.yml";

    private LayoutTextsMigration() {}

    /** Call before {@code LanguageManager} and {@code GuiLayoutConfig} read the config. */
    public static void run(SmartSpawner plugin) {
        if (migrate(plugin.getDataFolder(), plugin::getResource, LocalDate.now(), plugin.getLogger())) {
            // config.yml was rewritten on disk, so Bukkit's cached copy is stale.
            plugin.reloadConfig();
        }
    }

    /**
     * Takes the data folder and a resource lookup rather than the plugin so it can be exercised without
     * a server.
     *
     * @return true when {@code config.yml} was rewritten and the caller must reload it
     */
    static boolean migrate(File dataFolder, Function<String, InputStream> resources, LocalDate today, Logger log) {
        File languageDir = new File(dataFolder, "language");
        File configFile = new File(dataFolder, "config.yml");
        YamlConfiguration config = configFile.isFile() ? YamlConfiguration.loadConfiguration(configFile) : null;
        String activeLocale = config == null ? BASE_LOCALE : config.getString("language", BASE_LOCALE);
        boolean activeIsLegacy = LEGACY_LOCALES.containsKey(activeLocale);

        List<String> folders = LEGACY_LOCALES.keySet().stream()
                .filter(locale -> new File(languageDir, locale).isDirectory())
                .sorted()
                .toList();
        if (folders.isEmpty() && !activeIsLegacy) return false;

        String backup = LanguageBackup.create(dataFolder, today, log);
        if (backup == null) {
            log.warning("Left the DonutSMP language folders as they are, because the backup failed.");
            return false;
        }

        // The config first: a server pointing at a deleted locale would start with no text at all.
        boolean configChanged = false;
        if (activeIsLegacy) {
            String layout = LEGACY_LOCALES.get(activeLocale);
            config.set("language", BASE_LOCALE);
            if (DEFAULT_LAYOUT.equals(config.getString("gui_layout", DEFAULT_LAYOUT))) {
                config.set("gui_layout", layout);
            }
            try {
                config.save(configFile);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to switch config.yml from language " + activeLocale + " to "
                        + BASE_LOCALE + "; the DonutSMP language folders were left in place", e);
                return false;
            }
            configChanged = true;
            log.info("Changed language: " + activeLocale + " to language: " + BASE_LOCALE
                    + " with gui_layout: " + config.getString("gui_layout") + ". The DonutSMP look is now a GUI layout.");
        }

        for (String locale : folders) {
            File localeDir = new File(languageDir, locale);
            String layout = LEGACY_LOCALES.get(locale);
            try {
                int texts = moveEditedTexts(dataFolder, localeDir, layout, resources);
                int files = locale.equals(activeLocale)
                        ? copyEditedFiles(localeDir, new File(languageDir, BASE_LOCALE), resources)
                        : 0;
                deleteRecursively(localeDir);
                log.info("Moved language/" + locale + " into gui_layouts/" + layout + "/" + TEXTS_FILE
                        + " (" + texts + " edited texts kept"
                        + (files > 0 ? ", " + files + " edited files copied to language/" + BASE_LOCALE : "")
                        + "). Backup: " + backup + "/");
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to migrate language/" + locale + ". Backup: " + backup + "/", e);
            }
        }
        return configChanged;
    }

    /** Copies each GUI text the owner changed into the layout's texts.yml. Returns how many. */
    private static int moveEditedTexts(File dataFolder, File localeDir, String layout,
                                       Function<String, InputStream> resources) throws IOException {
        File guiFile = new File(localeDir, GUI_FILE);
        if (!guiFile.isFile()) return 0;

        String textsResource = "gui_layouts/" + layout + "/" + TEXTS_FILE;
        YamlConfiguration shippedTexts = loadResource(resources, textsResource);
        YamlConfiguration shippedBase = loadResource(resources, "language/" + BASE_LOCALE + "/" + GUI_FILE);

        YamlConfiguration user = YamlConfiguration.loadConfiguration(guiFile);
        File textsFile = new File(dataFolder, textsResource);
        YamlConfiguration texts = null;
        int kept = 0;
        for (String path : user.getKeys(true)) {
            if (user.isConfigurationSection(path)) continue;
            Object value = user.get(path);
            Object shipped = shippedTexts.contains(path) ? shippedTexts.get(path) : shippedBase.get(path);
            if (sameText(value, shipped)) continue;

            if (texts == null) {
                extractIfMissing(resources, textsResource, textsFile);
                texts = YamlConfiguration.loadConfiguration(textsFile);
            }
            texts.set(path, value);
            kept++;
        }
        if (texts != null) texts.save(textsFile);
        return kept;
    }

    /**
     * Copies the files of the formerly active locale that the owner edited over {@code en_US}'s, so what
     * players saw does not change. Unedited files are skipped: they match the shipped {@code en_US}.
     */
    private static int copyEditedFiles(File localeDir, File baseDir, Function<String, InputStream> resources)
            throws IOException {
        File[] files = localeDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals(GUI_FILE));
        if (files == null) return 0;

        int copied = 0;
        for (File file : files) {
            File target = new File(baseDir, file.getName());
            YamlConfiguration shipped = loadResource(resources, "language/" + BASE_LOCALE + "/" + file.getName());
            if (target.exists() && !differs(YamlConfiguration.loadConfiguration(file), shipped)) continue;

            Files.createDirectories(baseDir.toPath());
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        return copied;
    }

    /** True when a value differs, or the owner removed a shipped key (a message component, say). */
    private static boolean differs(YamlConfiguration user, YamlConfiguration shipped) {
        for (String path : user.getKeys(true)) {
            if (!user.isConfigurationSection(path) && !sameText(user.get(path), shipped.get(path))) {
                return true;
            }
        }
        for (String path : shipped.getKeys(true)) {
            if (!user.contains(path)) return true;
        }
        return false;
    }

    /**
     * Equal once rendered. Shipped text moved from legacy codes to MiniMessage, so an owner's unedited
     * {@code &a} value must still match the shipped {@code <green>} one.
     */
    static boolean sameText(Object a, Object b) {
        if (a instanceof String first && b instanceof String second) {
            return first.equals(second) || TextFormatter.toLegacy(first).equals(TextFormatter.toLegacy(second));
        }
        if (a instanceof List<?> first && b instanceof List<?> second) {
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) {
                if (!sameText(first.get(i), second.get(i))) return false;
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    private static YamlConfiguration loadResource(Function<String, InputStream> resources, String path) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream stream = resources.apply(path)) {
            if (stream != null) {
                config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // A missing or broken resource compares as empty, so every owner value counts as edited and is kept.
        }
        return config;
    }

    private static void extractIfMissing(Function<String, InputStream> resources, String path, File target)
            throws IOException {
        if (target.exists()) return;
        Files.createDirectories(target.getParentFile().toPath());
        try (InputStream stream = resources.apply(path)) {
            if (stream != null) {
                Files.copy(stream, target.toPath());
            }
        }
    }

    private static void deleteRecursively(File dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
