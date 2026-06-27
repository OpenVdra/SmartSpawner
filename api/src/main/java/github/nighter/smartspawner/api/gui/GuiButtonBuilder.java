package github.nighter.smartspawner.api.gui;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating {@link GuiButtonData} instances programmatically.
 * Used in the SmartSpawner Developer API to define custom GUI buttons.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * GuiButtonData button = new GuiButtonBuilder()
 *     .slot(11)
 *     .material(Material.CHEST)
 *     .action("click", "open_storage")
 *     .build();
 * }</pre>
 */
public class GuiButtonBuilder {

    private String buttonType;
    private int slot = 0;
    private Material material = Material.STONE;
    private boolean enabled = true;
    private String condition = null;
    private final Map<String, String> actions = new HashMap<>();
    private boolean infoButton = false;
    private String customTexture = null;
    private long cooldownTicks = 0L;
    private final Map<String, List<GuiButtonSoundData>> clickSounds = new HashMap<>();
    private final Map<String, List<GuiButtonSoundData>> successSounds = new HashMap<>();
    private final Map<String, List<GuiButtonSoundData>> failSounds = new HashMap<>();

    /**
     * Sets the button type identifier.
     *
     * @param buttonType the button type (e.g., "slot_11")
     * @return this builder
     */
    public GuiButtonBuilder buttonType(String buttonType) {
        this.buttonType = buttonType;
        return this;
    }

    /**
     * Sets the slot position for this button.
     *
     * @param slot the 1-based slot number used by SmartSpawner YAML layouts
     * @return this builder
     */
    public GuiButtonBuilder slot(int slot) {
        this.slot = slot;
        return this;
    }

    /**
     * Sets the material for this button.
     *
     * @param material the Bukkit material
     * @return this builder
     */
    public GuiButtonBuilder material(Material material) {
        this.material = material;
        return this;
    }

    /**
     * Sets whether this button is enabled.
     *
     * @param enabled true if enabled
     * @return this builder
     */
    public GuiButtonBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Sets a condition for this button to be shown.
     *
     * @param condition the condition string (e.g., "sell_integration")
     * @return this builder
     */
    public GuiButtonBuilder condition(String condition) {
        this.condition = condition;
        return this;
    }

    /**
     * Adds an action for a specific click type.
     *
     * @param clickType the click type (e.g., "click", "left_click", "right_click")
     * @param action the action to perform
     * @return this builder
     */
    public GuiButtonBuilder action(String clickType, String action) {
        this.actions.put(clickType, action);
        return this;
    }

    /**
     * Marks this button as the spawner info button.
     *
     * @return this builder
     */
    public GuiButtonBuilder infoButton() {
        this.infoButton = true;
        return this;
    }

    /**
     * Sets whether this button is the spawner info button.
     *
     * @param infoButton true if info button
     * @return this builder
     */
    public GuiButtonBuilder infoButton(boolean infoButton) {
        this.infoButton = infoButton;
        return this;
    }

    /**
     * Sets a custom texture hash for PLAYER_HEAD material.
     *
     * @param customTexture the texture hash (without URL prefix)
     * @return this builder
     */
    public GuiButtonBuilder customTexture(String customTexture) {
        this.customTexture = customTexture;
        return this;
    }

    /**
     * Sets a button cooldown in server ticks. Zero disables cooldown.
     *
     * @param cooldownTicks cooldown in server ticks
     * @return this builder
     */
    public GuiButtonBuilder cooldownTicks(long cooldownTicks) {
        if (cooldownTicks < 0L) {
            throw new IllegalArgumentException("Button cooldown cannot be negative");
        }
        this.cooldownTicks = cooldownTicks;
        return this;
    }

    /**
     * Sets a button cooldown using SmartSpawner's config duration format.
     *
     * @param duration duration such as "5s", "1m_30s", or a numeric tick value
     * @return this builder
     */
    public GuiButtonBuilder cooldown(String duration) {
        return cooldownTicks(GuiDurationParser.parseToTicks(duration));
    }

    /**
     * Sets the sound played when the button click is accepted.
     *
     * @param soundName Bukkit sound key
     * @return this builder
     */
    public GuiButtonBuilder sound(String soundName) {
        return sound(soundName, 1.0f, 1.0f);
    }

    /**
     * Sets the sound played when the button click is accepted.
     *
     * @param soundName Bukkit sound key
     * @param volume sound volume
     * @param pitch sound pitch
     * @return this builder
     */
    public GuiButtonBuilder sound(String soundName, float volume, float pitch) {
        this.clickSounds.put(
                "click", List.of(new GuiButtonSoundData(soundName, volume, pitch)));
        return this;
    }

    /**
     * Removes the configured click sound.
     *
     * @return this builder
     */
    public GuiButtonBuilder noSound() {
        this.clickSounds.remove("click");
        return this;
    }

    public GuiButtonBuilder successSound(String soundName) {
        return successSound(soundName, 1.0f, 1.0f);
    }

    public GuiButtonBuilder successSound(String soundName, float volume, float pitch) {
        this.successSounds.put(
                "click", List.of(new GuiButtonSoundData(soundName, volume, pitch)));
        return this;
    }

    public GuiButtonBuilder failSound(String soundName) {
        return failSound(soundName, 1.0f, 1.0f);
    }

    public GuiButtonBuilder failSound(String soundName, float volume, float pitch) {
        this.failSounds.put(
                "click", List.of(new GuiButtonSoundData(soundName, volume, pitch)));
        return this;
    }

    public GuiButtonBuilder sound(String clickType, String soundName,
                                  float volume, float pitch) {
        return addSound(clickSounds, clickType, soundName, volume, pitch);
    }

    public GuiButtonBuilder sound(String clickType, String soundName) {
        return sound(clickType, soundName, 1.0f, 1.0f);
    }

    public GuiButtonBuilder successSound(String clickType, String soundName,
                                         float volume, float pitch) {
        return addSound(successSounds, clickType, soundName, volume, pitch);
    }

    public GuiButtonBuilder successSound(String clickType, String soundName) {
        return successSound(clickType, soundName, 1.0f, 1.0f);
    }

    public GuiButtonBuilder failSound(String clickType, String soundName,
                                      float volume, float pitch) {
        return addSound(failSounds, clickType, soundName, volume, pitch);
    }

    public GuiButtonBuilder failSound(String clickType, String soundName) {
        return failSound(clickType, soundName, 1.0f, 1.0f);
    }

    /**
     * Builds the {@link GuiButtonData} instance.
     *
     * @return the immutable button data
     */
    public GuiButtonData build() {
        return new GuiButtonData(buttonType, slot, material, enabled, condition,
                new HashMap<>(actions), infoButton, customTexture, cooldownTicks,
                copySounds(clickSounds), copySounds(successSounds), copySounds(failSounds));
    }

    private GuiButtonBuilder addSound(
            Map<String, List<GuiButtonSoundData>> target,
            String clickType, String soundName, float volume, float pitch) {
        if (clickType == null || clickType.isBlank()) {
            throw new IllegalArgumentException("Click type cannot be blank");
        }
        target.computeIfAbsent(clickType, ignored -> new ArrayList<>())
                .add(new GuiButtonSoundData(soundName, volume, pitch));
        return this;
    }

    private Map<String, List<GuiButtonSoundData>> copySounds(
            Map<String, List<GuiButtonSoundData>> source) {
        Map<String, List<GuiButtonSoundData>> copy = new HashMap<>();
        source.forEach((clickType, sounds) -> copy.put(clickType, List.copyOf(sounds)));
        return copy;
    }
}
