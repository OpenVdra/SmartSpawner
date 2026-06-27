package github.nighter.smartspawner.api.gui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for loading {@link GuiLayout} instances from YAML files.
 * Extracted from {@link github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig}
 * to support external plugin layout registration.
 */
public class ExternalGuiLayoutLoader {

    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 9;
    private static final int SLOT_OFFSET = 44;
    private static final int MAIN_GUI_SIZE = 27;
    private static final int SELL_CONFIRM_GUI_SIZE = 27;
    private static final String[] CLICK_TYPES = {
            "click", "left_click", "right_click", "shift_left_click", "shift_right_click"
    };

    private final SmartSpawner plugin;
    private final Logger logger;

    public ExternalGuiLayoutLoader(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Loads a GuiLayout from a YAML file.
     *
     * @param file the YAML file
     * @param layoutType "main", "storage", or "sell_confirm"
     * @return the loaded layout, or null if loading failed
     */
    public GuiLayout loadLayout(File file, String layoutType) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            return loadLayout(config, layoutType);
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to load " + layoutType + " layout from " + file.getName() + ": " + e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * Loads a GuiLayout from an already parsed FileConfiguration.
     *
     * @param config the file configuration
     * @param layoutType "main", "storage", or "sell_confirm"
     * @return the loaded layout, or null if loading failed
     */
    public GuiLayout loadLayout(FileConfiguration config, String layoutType) {
        GuiLayout layout = new GuiLayout();

        Set<String> buttonKeys = config.getKeys(false);
        if (buttonKeys.isEmpty()) {
            if (logger != null) {
                logger.warning("No buttons found in GUI layout");
            }
            return layout;
        }

        for (String buttonKey : buttonKeys) {
            if (!buttonKey.startsWith("slot_")) {
                continue;
            }
            if (!loadButton(config, layout, buttonKey, layoutType)) {
                if (logger != null) {
                    logger.warning("Failed to load button: " + buttonKey);
                }
            }
        }

        return layout;
    }

    private boolean loadButton(FileConfiguration config, GuiLayout layout, String buttonKey, String layoutType) {
        int slot = parseSlotFromKey(buttonKey);
        if (slot == -1) {
            if (logger != null) {
                logger.warning("Invalid button key format: " + buttonKey);
            }
            return false;
        }

        if (!config.getBoolean(buttonKey + ".enabled", true)) {
            return false;
        }

        String materialName = config.getString(buttonKey + ".material", "STONE");
        String condition = config.getString(buttonKey + ".condition", null);
        boolean infoButton = config.getBoolean(buttonKey + ".info_button", false);
        String customTexture = config.getString(buttonKey + ".custom_texture", null);
        long cooldownTicks = parseCooldown(config.getString(buttonKey + ".cooldown"), buttonKey);
        Map<String, List<GuiButtonSoundData>> clickSounds = new HashMap<>();
        Map<String, List<GuiButtonSoundData>> successSounds = new HashMap<>();
        Map<String, List<GuiButtonSoundData>> failSounds = new HashMap<>();
        parseLegacyButtonSounds(
                config, buttonKey, clickSounds, successSounds, failSounds);

        if (!isValidSlot(slot, layoutType)) {
            if (logger != null) {
                logger.warning(String.format("Invalid slot %d for button %s in %s layout", slot, buttonKey, layoutType));
            }
            return false;
        }

        if (condition != null && !evaluateCondition(condition)) {
            return false;
        }

        Material material = parseMaterial(materialName, buttonKey);
        int actualSlot = calculateActualSlot(slot, layoutType);

        Map<String, String> actions = new HashMap<>();
        ConfigurationSection actionSection = config.getConfigurationSection(buttonKey);

        ConfigurationSection ifSection = config.getConfigurationSection(buttonKey + ".if");
        if (ifSection != null) {
            for (String conditionKey : ifSection.getKeys(false)) {
                if (evaluateCondition(conditionKey)) {
                    ConfigurationSection conditionActions = ifSection.getConfigurationSection(conditionKey);
                    if (conditionActions != null) {
                        String conditionalMaterial = conditionActions.getString("material");
                        if (conditionalMaterial != null) {
                            material = parseMaterial(conditionalMaterial, buttonKey);
                        }

                        String conditionalCustomTexture = conditionActions.getString("custom_texture");
                        if (conditionalCustomTexture != null) {
                            customTexture = conditionalCustomTexture;
                        }

                        if (conditionActions.contains("cooldown")) {
                            cooldownTicks = parseCooldown(
                                    conditionActions.getString("cooldown"), buttonKey + ".if." + conditionKey);
                        }
                        parseLegacyButtonSounds(conditionActions, "", clickSounds,
                                successSounds, failSounds);
                        parseClickActions(conditionActions, actions, clickSounds,
                                successSounds, failSounds,
                                buttonKey + ".if." + conditionKey);
                    }
                    break;
                }
            }
        } else if (actionSection != null) {
            parseClickActions(actionSection, actions, clickSounds, successSounds, failSounds,
                    buttonKey);
        }

        GuiButton button = new GuiButton(buttonKey, actualSlot, material, true, condition, actions,
                infoButton, customTexture, cooldownTicks, clickSounds, successSounds, failSounds);
        layout.addButton(buttonKey, button);
        return true;
    }

    private long parseCooldown(String value, String buttonKey) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return GuiDurationParser.parseToTicks(value);
        } catch (IllegalArgumentException | ArithmeticException e) {
            if (logger != null) {
                logger.warning("Invalid cooldown '" + value + "' for button " + buttonKey
                        + ". Cooldown disabled.");
            }
            return 0L;
        }
    }

    private void parseClickActions(
            ConfigurationSection section,
            Map<String, String> actions,
            Map<String, List<GuiButtonSoundData>> clickSounds,
            Map<String, List<GuiButtonSoundData>> successSounds,
            Map<String, List<GuiButtonSoundData>> failSounds,
            String context) {
        for (String clickType : CLICK_TYPES) {
            if (!section.contains(clickType)) {
                continue;
            }

            ConfigurationSection clickSection = section.getConfigurationSection(clickType);
            if (clickSection == null) {
                String action = section.getString(clickType);
                if (action != null && !action.isBlank()) {
                    actions.put(clickType, action);
                }
                continue;
            }

            String action = clickSection.getString("action");
            if (action != null && !action.isBlank()) {
                actions.put(clickType, action);
            } else if (logger != null) {
                logger.warning("Missing action for " + context + "." + clickType);
            }

            putSounds(clickSection, "sound", clickType, clickSounds,
                    context + "." + clickType + ".sound");
            putSounds(clickSection, "sound_success", clickType, successSounds,
                    context + "." + clickType + ".sound_success");
            putSounds(clickSection, "sound_fail", clickType, failSounds,
                    context + "." + clickType + ".sound_fail");
        }
    }

    private void parseLegacyButtonSounds(
            ConfigurationSection section,
            String basePath,
            Map<String, List<GuiButtonSoundData>> clickSounds,
            Map<String, List<GuiButtonSoundData>> successSounds,
            Map<String, List<GuiButtonSoundData>> failSounds) {
        String soundPath = path(basePath, "sound");
        if (!section.contains(soundPath)) {
            return;
        }

        ConfigurationSection soundSection = section.getConfigurationSection(soundPath);
        if (soundSection != null
                && (soundSection.contains("success") || soundSection.contains("fail"))) {
            if (soundSection.contains("name")) {
                clickSounds.put("click", parseSounds(soundSection, "", soundPath));
            }
            putSounds(soundSection, "success", "click", successSounds,
                    soundPath + ".success");
            putSounds(soundSection, "fail", "click", failSounds, soundPath + ".fail");
            return;
        }

        clickSounds.put("click", parseSounds(section, soundPath, soundPath));
    }

    private void putSounds(
            ConfigurationSection section,
            String path,
            String clickType,
            Map<String, List<GuiButtonSoundData>> target,
            String context) {
        if (section.contains(path)) {
            target.put(clickType, parseSounds(section, path, context));
        }
    }

    private List<GuiButtonSoundData> parseSounds(
            ConfigurationSection section, String soundPath, String context) {
        Object raw = soundPath.isEmpty() ? section : section.get(soundPath);
        List<GuiButtonSoundData> sounds = new ArrayList<>();

        if (raw instanceof String soundName) {
            addSound(sounds, soundName, 1.0f, 1.0f, context);
        } else if (raw instanceof List<?> values) {
            for (Object value : values) {
                parseSoundValue(sounds, value, context);
            }
        } else if (raw instanceof ConfigurationSection soundSection) {
            addSound(sounds, soundSection.getString("name"),
                    (float) soundSection.getDouble("volume", 1.0),
                    (float) soundSection.getDouble("pitch", 1.0), context);
        } else if (raw instanceof Map<?, ?> soundMap) {
            addSound(sounds, stringValue(soundMap.get("name")),
                    floatValue(soundMap.get("volume"), 1.0f),
                    floatValue(soundMap.get("pitch"), 1.0f), context);
        } else if (raw != null && logger != null) {
            logger.warning("Invalid sound value at " + context);
        }

        return List.copyOf(sounds);
    }

    private void parseSoundValue(
            List<GuiButtonSoundData> sounds, Object value, String context) {
        if (value instanceof String soundName) {
            addSound(sounds, soundName, 1.0f, 1.0f, context);
        } else if (value instanceof Map<?, ?> soundMap) {
            addSound(sounds, stringValue(soundMap.get("name")),
                    floatValue(soundMap.get("volume"), 1.0f),
                    floatValue(soundMap.get("pitch"), 1.0f), context);
        } else if (value instanceof ConfigurationSection soundSection) {
            addSound(sounds, soundSection.getString("name"),
                    (float) soundSection.getDouble("volume", 1.0),
                    (float) soundSection.getDouble("pitch", 1.0), context);
        } else if (logger != null) {
            logger.warning("Invalid sound entry at " + context);
        }
    }

    private void addSound(
            List<GuiButtonSoundData> sounds, String name, float volume, float pitch,
            String context) {
        if (name == null || name.isBlank() || "none".equalsIgnoreCase(name)) {
            return;
        }
        try {
            sounds.add(new GuiButtonSoundData(name, volume, pitch));
        } catch (IllegalArgumentException e) {
            if (logger != null) {
                logger.warning("Invalid sound configuration at " + context + ": "
                        + e.getMessage());
            }
        }
    }

    private String path(String basePath, String child) {
        return basePath == null || basePath.isBlank() ? child : basePath + "." + child;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private float floatValue(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private int parseSlotFromKey(String buttonKey) {
        if (!buttonKey.startsWith("slot_")) {
            return -1;
        }
        try {
            String slotPart = buttonKey.substring(5);
            int underscoreIndex = slotPart.indexOf('_');
            if (underscoreIndex > 0) {
                slotPart = slotPart.substring(0, underscoreIndex);
            }
            return Integer.parseInt(slotPart);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean isValidSlot(int slot, String layoutType) {
        return slot >= getMinSlot(layoutType) && slot <= getMaxSlot(layoutType);
    }

    private int getMinSlot(String layoutType) {
        return "storage".equals(layoutType) ? MIN_SLOT : 1;
    }

    private int getMaxSlot(String layoutType) {
        if ("storage".equals(layoutType)) {
            return MAX_SLOT;
        } else if ("sell_confirm".equals(layoutType)) {
            return SELL_CONFIRM_GUI_SIZE;
        } else {
            return MAIN_GUI_SIZE;
        }
    }

    private int calculateActualSlot(int slot, String layoutType) {
        if ("storage".equals(layoutType)) {
            return SLOT_OFFSET + slot;
        } else {
            return slot - 1;
        }
    }

    private Material parseMaterial(String materialName, String buttonKey) {
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            if (logger != null) {
                logger.warning(String.format("Invalid material %s for button %s. Using STONE.", materialName, buttonKey));
            }
            return Material.STONE;
        }
    }

    private boolean evaluateCondition(String condition) {
        if (plugin == null) return true;
        switch (condition) {
            case "sell_integration":
                return plugin.hasSellIntegration();
            case "no_sell_integration":
                return !plugin.hasSellIntegration();
            default:
                if (logger != null) {
                    logger.warning("Unknown condition: " + condition);
                }
                return true;
        }
    }
}
