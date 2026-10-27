package github.nighter.smartspawner.language.file;

import lombok.Getter;

/** The files in one {@code language/<locale>/} folder. */
@Getter
public enum LanguageFileType {
    MESSAGES("messages.yml"),
    GUI("gui.yml"),
    ITEMS("items.yml"),
    FORMATTING("formatting.yml");

    private final String fileName;

    LanguageFileType(String fileName) {
        this.fileName = fileName;
    }
}
