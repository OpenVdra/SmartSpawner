package github.nighter.smartspawner.spawner.gui.layout;

import lombok.Getter;
import org.bukkit.Material;

import java.util.Map;

@Getter
public class GuiButton {
    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;
    private final String condition;
    private final Map<String, String> actions;
    private final boolean infoButton; // Marks this as spawner info button for timer updates
    private final String customTexture; // Custom texture for PLAYER_HEAD material

    public GuiButton(String buttonType, int slot, Material material, boolean enabled, String condition, Map<String, String> actions, boolean infoButton, String customTexture) {
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
        this.condition = condition;
        this.actions = actions;
        this.infoButton = infoButton;
        this.customTexture = customTexture;
    }

    public String getAction(String clickType) {
        return actions != null ? actions.get(clickType) : null;
    }

    /**
     * Get action for click type with fallback to "click" for any-click actions
     * OPTIMIZATION: Check specific click type first, then fallback to "click"
     * @param clickType The specific click type (e.g., "left_click", "right_click")
     * @return The action string, or null if no action found
     */
    public String getActionWithFallback(String clickType) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        // First check for specific click type action
        String specificAction = actions.get(clickType);
        if (specificAction != null && !specificAction.isEmpty()) {
            return specificAction;
        }

        // Fallback to generic "click" action (matches any click)
        return actions.get("click");
    }

    public String getDefaultAction() {
        // OPTIMIZATION: Check "click" first as it's most common, then fallback to "default"
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        String clickAction = actions.get("click");
        if (clickAction != null && !clickAction.isEmpty()) {
            return clickAction;
        }

        return actions.get("default");
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }
}