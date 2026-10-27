package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.file.LanguageFileType;
import github.nighter.smartspawner.language.file.LanguageFiles;
import github.nighter.smartspawner.language.file.LegacyLanguageKeys;
import github.nighter.smartspawner.language.file.LegacyLanguageKeys.OldFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves every folder under {@code language/} from the 1.7 layout to the 1.8 one, once:
 * {@code command_messages.yml} into {@code messages.yml} under {@code command}, {@code command_gui.yml}
 * into {@code gui.yml} under {@code command}, {@code hologram.yml} into {@code items.yml} under
 * {@code hologram}, {@code iridium_skyblock.yml} into {@code hooks/}, and every key renamed by
 * {@link LegacyLanguageKeys}. Each layout's {@code texts.yml} gets the same GUI renames.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Detection is the old files or old keys being there, never a version number, so a second run
 *       finds nothing and does nothing.</li>
 *   <li>Everything is backed up once ({@link LanguageBackup}) before anything is written.</li>
 *   <li>{@code messages.yml} and {@code gui.yml} are rebuilt from the shipped file, so they get its key
 *       order and comments, with every value the owner had put under its new name. A custom locale uses
 *       {@code en_US}'s file as the template. A message the owner has replaces the shipped one whole, so
 *       a component they removed stays removed.</li>
 *   <li>{@code items.yml} and {@code formatting.yml} keep the owner's keys exactly; only their comments
 *       are replaced. A mob section in {@code items.yml} leaves keys out on purpose, so adding the
 *       shipped ones would break its inheritance.</li>
 *   <li>No value is dropped. A key the table does not know stays under the same path (under
 *       {@code command} when it came from a command file) and is listed in the log.</li>
 *   <li>A value already at its new path wins over the old one being moved there.</li>
 * </ul>
 */
public final class LanguageRestructure {
    private static final Set<String> MESSAGE_COMPONENTS =
            Set.of("message", "title", "subtitle", "action_bar", "sound", "enabled");
    private static final String TEXTS_FILE = "texts.yml";

    private LanguageRestructure() {}

    /**
     * Runs every language file migration that moves or rewrites files, before {@code LanguageUpdater}
     * tops them up and before {@code LanguageManager} and {@code GuiLayoutConfig} read anything.
     * They share one backup.
     */
    public static void run(SmartSpawner plugin) {
        File dataFolder = plugin.getDataFolder();
        Supplier<String> backup = LanguageBackup.once(dataFolder, LocalDate.now(), plugin.getLogger());
        migrate(dataFolder, plugin::getResource, backup, plugin.getLogger());
        if (LayoutTextsMigration.migrate(dataFolder, plugin::getResource, backup, plugin.getLogger())) {
            // config.yml was rewritten on disk, so Bukkit's cached copy is stale.
            plugin.reloadConfig();
        }
    }

    /**
     * @param resources reads a bundled resource by path, or returns {@code null}
     * @param backup    creates the backup the first time it is called and returns its folder name, or
     *                  {@code null} if it could not be written
     * @return how many folders were migrated
     */
    static int migrate(File dataFolder, Function<String, InputStream> resources, Supplier<String> backup, Logger log) {
        List<File> localeDirs = new ArrayList<>();
        File[] folders = new File(dataFolder, "language").listFiles(File::isDirectory);
        if (folders != null) {
            Arrays.stream(folders).filter(LanguageRestructure::isOldLayout)
                    .sorted(Comparator.comparing(File::getName)).forEach(localeDirs::add);
        }
        List<File> textsFiles = new ArrayList<>();
        File[] layouts = new File(dataFolder, "gui_layouts").listFiles(File::isDirectory);
        if (layouts != null) {
            Arrays.stream(layouts).map(dir -> new File(dir, TEXTS_FILE))
                    .filter(file -> file.isFile() && hasOldKeys(YamlConfiguration.loadConfiguration(file), LegacyLanguageKeys.GUI))
                    .sorted(Comparator.comparing(File::getPath)).forEach(textsFiles::add);
        }
        if (localeDirs.isEmpty() && textsFiles.isEmpty()) return 0;

        String backupName = backup.get();
        if (backupName == null) {
            log.warning("Left the language files in the old format, because the backup failed. "
                    + "The plugin keeps running with the shipped texts for anything it cannot find.");
            return 0;
        }

        int migrated = 0;
        for (File localeDir : localeDirs) {
            try {
                List<String> unknown = migrateLocale(localeDir, resources);
                migrated++;
                log.info("Migrated language/" + localeDir.getName() + " to the new format. Backup: " + backupName + "/");
                if (!unknown.isEmpty()) {
                    log.info("Kept " + unknown.size() + " key(s) in language/" + localeDir.getName()
                            + " that SmartSpawner does not use, under the same name: " + String.join(", ", unknown));
                }
            } catch (IOException | RuntimeException e) {
                log.log(Level.SEVERE, "Failed to migrate language/" + localeDir.getName() + ". Backup: " + backupName + "/", e);
            }
        }
        for (File textsFile : textsFiles) {
            try {
                renameLayoutTexts(textsFile);
                log.info("Renamed the keys in gui_layouts/" + textsFile.getParentFile().getName() + "/" + TEXTS_FILE
                        + " to the new format. Backup: " + backupName + "/");
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to migrate " + textsFile.getPath() + ". Backup: " + backupName + "/", e);
            }
        }
        return migrated;
    }

    /** Old files present, or an old key at the top of messages.yml or gui.yml. */
    static boolean isOldLayout(File localeDir) {
        for (OldFile file : LegacyLanguageKeys.MERGED) {
            if (new File(localeDir, file.name()).isFile()) return true;
        }
        if (new File(localeDir, LegacyLanguageKeys.OLD_IRIDIUM_FILE).isFile()) return true;
        return hasOldKeys(load(new File(localeDir, LegacyLanguageKeys.MESSAGES.name())), LegacyLanguageKeys.MESSAGES)
                || hasOldKeys(load(new File(localeDir, LegacyLanguageKeys.GUI.name())), LegacyLanguageKeys.GUI);
    }

    /** Any key the rename table moves. Keys of the new layout never match it. */
    private static boolean hasOldKeys(YamlConfiguration config, OldFile file) {
        for (String path : leaves(config)) {
            if (LegacyLanguageKeys.move(file, path) != null) return true;
        }
        return false;
    }

    /** Returns the keys the rename table did not know. */
    private static List<String> migrateLocale(File localeDir, Function<String, InputStream> resources) throws IOException {
        String locale = localeDir.getName();
        String templateLocale = LanguageFiles.SUPPORTED_LANGUAGES.contains(locale) ? locale : LanguageFiles.BASE_LOCALE;
        List<String> unknown = new ArrayList<>();

        // Values that moved, then values already in the new layout, which win.
        Map<LanguageFileType, Map<String, Object>> moved = new EnumMap<>(LanguageFileType.class);
        Map<LanguageFileType, Map<String, Object>> current = new EnumMap<>(LanguageFileType.class);
        for (LanguageFileType type : LanguageFileType.values()) {
            moved.put(type, new LinkedHashMap<>());
            current.put(type, new LinkedHashMap<>());
        }

        collectSameFile(localeDir, LegacyLanguageKeys.MESSAGES, moved, current);
        collectSameFile(localeDir, LegacyLanguageKeys.GUI, moved, current);
        for (OldFile file : LegacyLanguageKeys.MERGED) {
            YamlConfiguration old = load(new File(localeDir, file.name()));
            for (String path : leaves(old)) {
                String target = LegacyLanguageKeys.move(file, path);
                if (target == null) {
                    target = file.defaultPrefix() + path;
                    unknown.add(file.name() + ": " + path);
                }
                moved.get(file.target()).put(target, old.get(path));
            }
        }

        // The single-spawner storage title used to fall back to the normal one. It is shipped now, so
        // an owner who changed only the normal title keeps seeing theirs on single spawners too.
        Map<String, Object> gui = moved.get(LanguageFileType.GUI);
        if (gui.containsKey("storage.title") && !gui.containsKey("storage.title_single")
                && !current.get(LanguageFileType.GUI).containsKey("storage.title_single")) {
            gui.put("storage.title_single", gui.get("storage.title"));
        }

        YamlConfiguration messagesTemplate = loadResource(resources, templateLocale, LanguageFileType.MESSAGES);
        writeMessages(new File(localeDir, LanguageFileType.MESSAGES.getFileName()), messagesTemplate,
                moved.get(LanguageFileType.MESSAGES), current.get(LanguageFileType.MESSAGES), unknown);
        YamlConfiguration guiTemplate = loadResource(resources, templateLocale, LanguageFileType.GUI);
        writeGui(new File(localeDir, LanguageFileType.GUI.getFileName()), guiTemplate,
                moved.get(LanguageFileType.GUI), current.get(LanguageFileType.GUI), unknown);
        recomment(new File(localeDir, LanguageFileType.ITEMS.getFileName()),
                loadResource(resources, templateLocale, LanguageFileType.ITEMS), moved.get(LanguageFileType.ITEMS));
        recomment(new File(localeDir, LanguageFileType.FORMATTING.getFileName()),
                loadResource(resources, templateLocale, LanguageFileType.FORMATTING), Map.of());

        for (OldFile file : LegacyLanguageKeys.MERGED) {
            Files.deleteIfExists(new File(localeDir, file.name()).toPath());
        }
        File iridium = new File(localeDir, LegacyLanguageKeys.OLD_IRIDIUM_FILE);
        if (iridium.isFile()) {
            File target = new File(localeDir, "hooks/" + LegacyLanguageKeys.OLD_IRIDIUM_FILE);
            if (target.exists()) {
                Files.delete(iridium.toPath());
            } else {
                Files.createDirectories(target.getParentFile().toPath());
                Files.move(iridium.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
        }
        return unknown;
    }

    /** A file that kept its name: old keys move, keys already in the new layout stay and win. */
    private static void collectSameFile(File localeDir, OldFile file,
                                        Map<LanguageFileType, Map<String, Object>> moved,
                                        Map<LanguageFileType, Map<String, Object>> current) {
        YamlConfiguration config = load(new File(localeDir, file.name()));
        for (String path : leaves(config)) {
            String target = LegacyLanguageKeys.move(file, path);
            if (target != null) moved.get(file.target()).put(target, config.get(path));
            else current.get(file.target()).put(path, config.get(path));
        }
    }

    private static void writeMessages(File target, YamlConfiguration template, Map<String, Object> moved,
                                      Map<String, Object> current, List<String> unknown) throws IOException {
        Shipped shipped = new Shipped(template);
        YamlConfiguration result = template;
        // An owner's message replaces the shipped one whole, so the components they removed stay removed.
        Set<String> cleared = new HashSet<>();
        for (Map<String, Object> values : List.of(moved, current)) {
            for (String path : values.keySet()) {
                String entry = messageEntry(path);
                if (entry != null && cleared.add(entry) && result.isConfigurationSection(entry)) {
                    List<String> comments = result.getComments(entry);
                    result.set(entry, null);
                    result.createSection(entry);
                    result.setComments(entry, comments);
                }
            }
        }
        apply(result, shipped, moved, current, unknown);
        result.save(target);
    }

    private static void writeGui(File target, YamlConfiguration template, Map<String, Object> moved,
                                 Map<String, Object> current, List<String> unknown) throws IOException {
        apply(template, new Shipped(template), moved, current, unknown);
        template.save(target);
    }

    private static void apply(YamlConfiguration result, Shipped shipped, Map<String, Object> moved,
                              Map<String, Object> current, List<String> unknown) {
        for (Map.Entry<String, Object> entry : moved.entrySet()) {
            if (!current.containsKey(entry.getKey())) result.set(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            result.set(entry.getKey(), entry.getValue());
            if (!shipped.knows(entry.getKey())) unknown.add(entry.getKey());
        }
    }

    /** The shipped file's keys, taken before the template is changed. */
    private record Shipped(Set<String> leaves, Set<String> sections) {
        Shipped(YamlConfiguration template) {
            this(new HashSet<>(LanguageRestructure.leaves(template)), new HashSet<>(LanguageRestructure.sections(template)));
        }

        /** Shipped, or a message component the owner added to a shipped message. */
        boolean knows(String path) {
            String entry = messageEntry(path);
            return leaves.contains(path) || entry != null && sections.contains(entry);
        }
    }

    /**
     * Keeps the owner's {@code items.yml} or {@code formatting.yml} as it is, adds the values moved into
     * it ({@code hologram.lines}), and takes the header and top-level comments from the shipped file.
     */
    private static void recomment(File target, YamlConfiguration template, Map<String, Object> moved) throws IOException {
        if (!target.isFile() && moved.isEmpty()) return;
        YamlConfiguration config = target.isFile() ? load(target) : new YamlConfiguration();
        for (Map.Entry<String, Object> entry : moved.entrySet()) {
            if (!config.contains(entry.getKey())) config.set(entry.getKey(), entry.getValue());
        }
        config.options().setHeader(template.options().getHeader());
        for (String key : config.getKeys(false)) {
            if (template.contains(key)) config.setComments(key, template.getComments(key));
            else config.setComments(key, List.of());
        }
        for (String path : config.getKeys(true)) {
            if (path.indexOf('.') < 0) continue;
            List<String> comments = template.contains(path) ? template.getComments(path) : List.of();
            config.setComments(path, comments);
            config.setInlineComments(path, template.contains(path) ? template.getInlineComments(path) : List.of());
        }
        config.save(target);
    }

    /** The message entry a leaf belongs to, or {@code null} for a plain text such as {@code prefix}. */
    private static String messageEntry(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 && MESSAGE_COMPONENTS.contains(path.substring(dot + 1)) ? path.substring(0, dot) : null;
    }

    /** texts.yml uses gui.yml's keys: rename them in place, keeping a value already at its new path. */
    private static void renameLayoutTexts(File file) throws IOException {
        YamlConfiguration config = load(file);
        YamlConfiguration result = new YamlConfiguration();
        result.options().setHeader(config.options().getHeader());
        Map<String, Object> current = new LinkedHashMap<>();
        Map<String, Object> moved = new LinkedHashMap<>();
        for (String path : leaves(config)) {
            String target = LegacyLanguageKeys.move(LegacyLanguageKeys.GUI, path);
            if (target != null) moved.put(target, config.get(path));
            else current.put(path, config.get(path));
        }
        if (moved.containsKey("storage.title") && !moved.containsKey("storage.title_single")
                && !current.containsKey("storage.title_single")) {
            moved.put("storage.title_single", moved.get("storage.title"));
        }
        moved.forEach((path, value) -> {
            if (!current.containsKey(path)) result.set(path, value);
        });
        current.forEach(result::set);
        result.save(file);
    }

    private static List<String> leaves(ConfigurationSection config) {
        List<String> result = new ArrayList<>();
        for (String path : config.getKeys(true)) {
            if (!config.isConfigurationSection(path)) result.add(path);
        }
        return result;
    }

    private static List<String> sections(ConfigurationSection config) {
        List<String> result = new ArrayList<>();
        for (String path : config.getKeys(true)) {
            if (config.isConfigurationSection(path)) result.add(path);
        }
        return result;
    }

    private static YamlConfiguration load(File file) {
        YamlConfiguration config = new YamlConfiguration();
        if (!file.isFile()) return config;
        try {
            config.load(file);
        } catch (Exception e) {
            throw new IllegalStateException(file.getName() + " is not valid YAML: " + e.getMessage(), e);
        }
        return config;
    }

    private static YamlConfiguration loadResource(Function<String, InputStream> resources, String locale,
                                                  LanguageFileType type) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream stream = resources.apply("language/" + locale + "/" + type.getFileName())) {
            if (stream != null) {
                config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IOException("The bundled " + type.getFileName() + " is not valid YAML", e);
        }
        return config;
    }
}
