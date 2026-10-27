package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.file.LanguageFileType;
import github.nighter.smartspawner.language.file.LanguageFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Keeps every language file in use in sync with the defaults. Version-less: on every startup missing
 * keys are topped up (values and comments) while user translations are preserved. See {@link YamlMigrator}.
 * Files that moved or merged in 1.8.0 are handled once, before this, by {@link LanguageRestructure}.
 */
public class LanguageUpdater {

    private final SmartSpawner plugin;

    public LanguageUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        checkAndUpdateLanguageFiles();
    }

    /**
     * For the active locale and every locale folder on disk (every bundled locale too while
     * {@code language_auto_detect} is on), ensures every language file is present and up-to-date. Files
     * are created if missing, otherwise merged-updated. User-customised values are preserved. A custom
     * locale (no bundled copy) only gets the renames: there is nothing to top up from, and a key it
     * lacks falls back to the server language and then to the shipped {@code en_US} at runtime.
     */
    public void checkAndUpdateLanguageFiles() {
        String activeLocale = plugin.getConfig().getString("language", LanguageFiles.BASE_LOCALE);
        boolean autoDetect = plugin.getConfig().getBoolean("language_auto_detect", false);
        for (String language : LanguageFiles.localesInUse(plugin.getDataFolder(), activeLocale, autoDetect)) {
            boolean bundled = LanguageFiles.SUPPORTED_LANGUAGES.contains(language);
            File langDir = new File(plugin.getDataFolder(), "language/" + language);
            if (!bundled && !langDir.isDirectory()) continue;
            langDir.mkdirs();

            for (LanguageFileType type : LanguageFileType.values()) {
                File langFile   = new File(langDir, type.getFileName());
                if (!bundled && !langFile.exists()) continue;
                String resource = "language/" + language + "/" + type.getFileName();
                List<YamlMigrator.Rename> renames = ConfigMigrations.forLanguageFile(type);

                switch (type) {
                    // items.yml: only top up the '<section>.default' keys; leave per-mob overrides alone.
                    case ITEMS -> YamlMigrator.migrate(langFile, plugin.getResource(resource), renames,
                            ConfigMigrations.ITEM_DEFAULTS, false, plugin.getLogger());
                    // Which parts of a message to send is the owner's choice. Once they have a
                    // message, its components are left alone, so deleting 'message' to leave only
                    // 'action_bar' sticks instead of coming back and sending both.
                    case MESSAGES -> YamlMigrator.migrate(langFile, plugin.getResource(resource), renames, null, true,
                            YamlMigrator.OwnedSection.restoredWhenAbsent(LanguageUpdater::isMessageEntry),
                            plugin.getLogger());
                    case GUI, FORMATTING -> YamlMigrator.migrate(langFile, plugin.getResource(resource), renames,
                            plugin.getLogger());
                }
            }
        }
    }

    /** The parts a single message is built from. A section holding any of these is one message. */
    private static final Set<String> MESSAGE_COMPONENTS =
            Set.of("message", "title", "subtitle", "action_bar", "sound", "enabled");

    /**
     * True when {@code path} is one message rather than a group of them.
     *
     * <p>Messages sit at different depths: {@code general.inventory_full} under its topic,
     * {@code command.list.no_spawners_found} under the command it belongs to. Those group sections
     * must not count: locking {@code command.list} as a whole would keep a message added to it by a
     * later update from ever reaching the file, which is the missing-key warning this is meant to
     * avoid. Asking what a section directly contains identifies a message at any depth.</p>
     */
    private static boolean isMessageEntry(YamlConfiguration defaults, String path) {
        ConfigurationSection section = defaults.getConfigurationSection(path);
        if (section == null) return false;
        for (String child : section.getKeys(false)) {
            if (MESSAGE_COMPONENTS.contains(child) && !section.isConfigurationSection(child)) {
                return true;
            }
        }
        return false;
    }
}
