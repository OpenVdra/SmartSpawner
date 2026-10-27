package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.file.LanguageFiles;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps every bundled language file in sync with the defaults. Version-less: on every startup missing
 * keys are topped up (values and comments) while user translations are preserved. See {@link YamlMigrator}.
 */
public class LanguageUpdater {

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
     * For the active locale and every locale folder on disk, ensures every language file is present and
     * up-to-date. Files are created if missing, otherwise merged-updated. User-customised values are
     * preserved. A custom locale (no bundled copy) only gets the renames: there is nothing to top up
     * from, and a key it lacks falls back to the shipped text at runtime.
     */
    public void checkAndUpdateLanguageFiles() {
        String activeLocale = plugin.getConfig().getString("language", "en_US");
        for (String language : LanguageFiles.localesInUse(plugin.getDataFolder(), activeLocale)) {
            boolean bundled = LanguageFiles.SUPPORTED_LANGUAGES.contains(language);
            File langDir = new File(plugin.getDataFolder(), "language/" + language);
            if (!bundled && !langDir.isDirectory()) continue;
            langDir.mkdirs();

            for (LanguageFileType type : activeFileTypes) {
                File langFile   = new File(langDir, type.getFileName());
                if (!bundled && !langFile.exists()) continue;
                String resource = "language/" + language + "/" + type.getFileName();
                List<YamlMigrator.Rename> renames = ConfigMigrations.forLanguageFile(type);

                if (type == LanguageFileType.ITEMS) {
                    // items.yml: only top up the '<section>.default' keys; leave per-mob overrides alone.
                    YamlMigrator.migrate(langFile, plugin.getResource(resource), renames,
                            ConfigMigrations.ITEM_DEFAULTS, false, plugin.getLogger());
                } else if (type == LanguageFileType.MESSAGES || type == LanguageFileType.COMMAND_MESSAGES) {
                    // Which parts of a message to send is the owner's choice. Once they have a
                    // message, its components are left alone, so deleting 'message' to leave only
                    // 'action_bar' sticks instead of coming back and sending both.
                    YamlMigrator.migrate(langFile, plugin.getResource(resource), renames, null, true,
                            YamlMigrator.OwnedSection.restoredWhenAbsent(LanguageUpdater::isMessageEntry),
                            plugin.getLogger());
                } else {
                    YamlMigrator.migrate(langFile, plugin.getResource(resource), renames,
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
     * <p>{@code messages.yml} is flat, so its messages sit at the top level. {@code command_messages.yml}
     * nests them one deeper under the command they belong to, and those command sections must not count:
     * locking {@code list} as a whole would keep a message added to it by a later update from ever
     * reaching the file, which is the missing-key warning this is meant to avoid. Asking what a section
     * directly contains identifies a message in either shape.</p>
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
