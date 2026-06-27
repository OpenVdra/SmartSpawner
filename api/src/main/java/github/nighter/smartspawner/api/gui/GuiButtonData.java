package github.nighter.smartspawner.api.gui;

import lombok.Getter;
import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable data transfer object representing a GUI button configuration.
 * This class is part of the SmartSpawner Developer API and can be used
 * to programmatically define GUI layouts via {@link GuiButtonBuilder}.
 */
@Getter
public class GuiButtonData {

    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;
    private final String condition;
    private final Map<String, String> actions;
    private final boolean infoButton;
    private final String customTexture;
    private final long cooldownTicks;
    private final GuiButtonSoundData clickSound;
    private final GuiButtonSoundData successSound;
    private final GuiButtonSoundData failSound;
    private final Map<String, List<GuiButtonSoundData>> clickSounds;
    private final Map<String, List<GuiButtonSoundData>> successSounds;
    private final Map<String, List<GuiButtonSoundData>> failSounds;

    /**
     * Creates button data using the same 1-based slot numbering as GUI layout YAML files.
     */
    public GuiButtonData(String buttonType, int slot, Material material, boolean enabled,
                         String condition, Map<String, String> actions, boolean infoButton,
                         String customTexture) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, 0L, (GuiButtonSoundData) null, null, null);
    }

    /**
     * Creates button data with optional cooldown and click sound settings.
     *
     * @param cooldownTicks cooldown in server ticks; zero disables cooldown
     * @param clickSound click sound configuration, or null for no sound
     */
    public GuiButtonData(String buttonType, int slot, Material material, boolean enabled,
                         String condition, Map<String, String> actions, boolean infoButton,
                         String customTexture, long cooldownTicks, GuiButtonSoundData clickSound) {
        this(buttonType, slot, material, enabled, condition, actions, infoButton,
                customTexture, cooldownTicks, clickSound, null, null);
    }

    public GuiButtonData(String buttonType, int slot, Material material, boolean enabled,
                         String condition, Map<String, String> actions, boolean infoButton,
                         String customTexture, long cooldownTicks, GuiButtonSoundData clickSound,
                         GuiButtonSoundData successSound, GuiButtonSoundData failSound) {
        if (cooldownTicks < 0L) {
            throw new IllegalArgumentException("Button cooldown cannot be negative");
        }
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
        this.condition = condition;
        this.actions = actions != null ? Collections.unmodifiableMap(new HashMap<>(actions)) : Collections.emptyMap();
        this.infoButton = infoButton;
        this.customTexture = customTexture;
        this.cooldownTicks = cooldownTicks;
        this.clickSound = clickSound;
        this.successSound = successSound;
        this.failSound = failSound;
        this.clickSounds = immutableSounds(clickSound);
        this.successSounds = immutableSounds(successSound);
        this.failSounds = immutableSounds(failSound);
    }

    public GuiButtonData(String buttonType, int slot, Material material, boolean enabled,
                         String condition, Map<String, String> actions, boolean infoButton,
                         String customTexture, long cooldownTicks,
                         Map<String, List<GuiButtonSoundData>> clickSounds,
                         Map<String, List<GuiButtonSoundData>> successSounds,
                         Map<String, List<GuiButtonSoundData>> failSounds) {
        if (cooldownTicks < 0L) {
            throw new IllegalArgumentException("Button cooldown cannot be negative");
        }
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
        this.condition = condition;
        this.actions = actions != null
                ? Collections.unmodifiableMap(new HashMap<>(actions))
                : Collections.emptyMap();
        this.infoButton = infoButton;
        this.customTexture = customTexture;
        this.cooldownTicks = cooldownTicks;
        this.clickSounds = immutableSounds(clickSounds);
        this.successSounds = immutableSounds(successSounds);
        this.failSounds = immutableSounds(failSounds);
        this.clickSound = firstSound(this.clickSounds);
        this.successSound = firstSound(this.successSounds);
        this.failSound = firstSound(this.failSounds);
    }

    /**
     * Gets the action for a specific click type.
     *
     * @param clickType the click type (e.g., "click", "left_click", "right_click")
     * @return the action string, or null if not found
     */
    public String getAction(String clickType) {
        return actions.get(clickType);
    }

    /**
     * Gets the default action (checks "click" first, then "default").
     *
     * @return the default action string, or null if not found
     */
    public String getDefaultAction() {
        String clickAction = actions.get("click");
        if (clickAction != null && !clickAction.isEmpty()) {
            return clickAction;
        }
        return actions.get("default");
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    /**
     * Returns whether this button has an enabled cooldown.
     */
    public boolean hasCooldown() {
        return cooldownTicks > 0L;
    }

    /**
     * Returns whether this button has a configured click sound.
     */
    public boolean hasClickSound() {
        return !clickSounds.isEmpty();
    }

    public boolean hasSuccessSound() {
        return !successSounds.isEmpty();
    }

    public boolean hasFailSound() {
        return !failSounds.isEmpty();
    }

    private static Map<String, List<GuiButtonSoundData>> immutableSounds(
            GuiButtonSoundData sound) {
        return sound == null ? Map.of() : Map.of("click", List.of(sound));
    }

    private static Map<String, List<GuiButtonSoundData>> immutableSounds(
            Map<String, List<GuiButtonSoundData>> sounds) {
        if (sounds == null || sounds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<GuiButtonSoundData>> copy = new HashMap<>();
        sounds.forEach((clickType, values) -> {
            if (clickType != null && values != null) {
                copy.put(clickType, List.copyOf(values));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static GuiButtonSoundData firstSound(
            Map<String, List<GuiButtonSoundData>> sounds) {
        List<GuiButtonSoundData> values = sounds.get("click");
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
