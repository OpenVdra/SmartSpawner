package github.nighter.smartspawner.api.gui;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable data transfer object representing a GUI layout configuration.
 * This class is part of the SmartSpawner Developer API and can be used
 * to programmatically define GUI layouts via {@link GuiLayoutBuilder}.
 */
@Getter
public class GuiLayoutData {

    private final GuiLayoutType type;
    private final Map<String, GuiButtonData> buttons;

    public GuiLayoutData(GuiLayoutType type, Map<String, GuiButtonData> buttons) {
        this.type = type;
        this.buttons = buttons != null ? Collections.unmodifiableMap(new HashMap<>(buttons)) : Collections.emptyMap();
    }
}
