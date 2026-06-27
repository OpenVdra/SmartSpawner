package github.nighter.smartspawner.api.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating {@link GuiLayoutData} instances programmatically.
 * Used in the SmartSpawner Developer API to define custom GUI layouts.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * GuiLayoutData layout = new GuiLayoutBuilder()
 *     .type(GuiLayoutType.MAIN_GUI)
 *     .addButton(new GuiButtonBuilder().slot(11).material(Material.CHEST).action("click", "open_storage").build())
 *     .build();
 * }</pre>
 */
public class GuiLayoutBuilder {

    private GuiLayoutType type = GuiLayoutType.MAIN_GUI;
    private final Map<String, GuiButtonData> buttons = new HashMap<>();

    /**
     * Sets the layout type.
     *
     * @param type the GUI layout type
     * @return this builder
     */
    public GuiLayoutBuilder type(GuiLayoutType type) {
        this.type = type;
        return this;
    }

    /**
     * Adds a button to this layout.
     *
     * @param button the button data
     * @return this builder
     */
    public GuiLayoutBuilder addButton(GuiButtonData button) {
        if (button == null) {
            throw new IllegalArgumentException("button cannot be null");
        }
        String key = button.getButtonType() != null ? button.getButtonType()
                : "slot_" + button.getSlot();
        this.buttons.put(key, button);
        return this;
    }

    /**
     * Adds a button with a specific key to this layout.
     *
     * @param key the button key (e.g., "slot_11")
     * @param button the button data
     * @return this builder
     */
    public GuiLayoutBuilder addButton(String key, GuiButtonData button) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        if (button == null) {
            throw new IllegalArgumentException("button cannot be null");
        }
        this.buttons.put(key, button);
        return this;
    }

    /**
     * Builds the {@link GuiLayoutData} instance.
     *
     * @return the immutable layout data
     */
    public GuiLayoutData build() {
        return new GuiLayoutData(type, new HashMap<>(buttons));
    }
}
