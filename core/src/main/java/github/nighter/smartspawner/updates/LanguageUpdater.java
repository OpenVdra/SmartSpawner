package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LanguageUpdater {
    private static final String VERSION_KEY = "language_version";
    private static final String[] SUPPORTED_LANGUAGES = {"en_US", "vi_VN", "de_DE", "en_US_DonutSMP", "en_US_DonutSMP_v2"};

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
        for (String language : SUPPORTED_LANGUAGES) {
            File langDir = new File(plugin.getDataFolder(), "language/" + language);
            langDir.mkdirs();

            for (LanguageFileType type : activeFileTypes) {
                File langFile = new File(langDir, type.getFileName());
                String resource = "language/" + language + "/" + type.getFileName();
                ConfigVersionService.updateFile(plugin, langFile, resource, VERSION_KEY);
            }
        }
    }
}
