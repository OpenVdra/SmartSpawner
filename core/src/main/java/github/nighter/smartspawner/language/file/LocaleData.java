package github.nighter.smartspawner.language.file;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One locale's files. {@code fallback} is where a missing message or GUI text is looked up next: a
 * player's locale falls back to the server's, and the server's to the bundled {@code en_US}. It is
 * {@code null} at the end of the chain.
 */
public record LocaleData(String locale, YamlConfiguration messages, YamlConfiguration gui,
                         YamlConfiguration items, YamlConfiguration formatting, LocaleData fallback) {

    public static LocaleData empty(String locale, LocaleData fallback) {
        return new LocaleData(locale, new YamlConfiguration(), new YamlConfiguration(), new YamlConfiguration(),
                new YamlConfiguration(), fallback);
    }

    public YamlConfiguration file(LanguageFileType type) {
        return switch (type) {
            case MESSAGES -> messages;
            case GUI -> gui;
            case ITEMS -> items;
            case FORMATTING -> formatting;
        };
    }
}
