package github.nighter.smartspawner.commands.list.gui.list.enums;

import lombok.Getter;

@Getter
public enum SortOption {
    DEFAULT("sort.option_name.default"),
    STACK_SIZE_DESC("sort.option_name.stack_size_desc"),
    STACK_SIZE_ASC("sort.option_name.stack_size_asc");


    private final String langPath;

    SortOption(String langPath) {
        this.langPath = langPath;
    }

    public SortOption getNextOption() {
        return switch (this) {
            case STACK_SIZE_DESC -> STACK_SIZE_ASC;
            case STACK_SIZE_ASC -> DEFAULT;
            case DEFAULT -> STACK_SIZE_DESC;
        };
    }

    public String getColorPath() {
        return langPath + ".color";
    }

    public String getName() {
        return name().toLowerCase();
    }
}
