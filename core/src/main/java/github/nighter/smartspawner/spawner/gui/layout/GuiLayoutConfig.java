package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.GuiLayoutUpdater;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class GuiLayoutConfig {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String STORAGE_GUI_FILE = "storage_gui.yml";
    private static final String MAIN_GUI_FILE = "main_gui.yml";
    private static final String SELL_CONFIRM_GUI_FILE = "sell_confirm_gui.yml";
    private static final String DEFAULT_LAYOUT = "default";
    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 9;
    private static final int SLOT_OFFSET = 44;
    private static final int MAIN_GUI_SIZE = 27;
    private static final int SELL_CONFIRM_GUI_SIZE = 27;

    private final SmartSpawner plugin;
    private final File layoutsDir;
    private final GuiLayoutUpdater layoutUpdater;
    private String currentLayout;
    @Getter
    private GuiLayout currentStorageLayout;
    @Getter
    private GuiLayout currentMainLayout;
    @Getter
    private GuiLayout currentSellConfirmLayout;
    @Getter
    private boolean skipMainGui;
    @Getter
    private boolean skipSellConfirmation;

    public GuiLayoutConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        this.layoutUpdater = new GuiLayoutUpdater(plugin);
        loadLayout();
    }

    public void loadLayout() {
        this.currentLayout = plugin.getConfig().getString("gui_layout", DEFAULT_LAYOUT);
        initializeLayoutsDirectory();
        
        // Check and update layout files before loading
        layoutUpdater.checkAndUpdateLayouts();

        this.currentStorageLayout = loadCurrentStorageLayout();
        this.currentMainLayout = loadCurrentMainLayout();
        this.currentSellConfirmLayout = loadCurrentSellConfirmLayout();

        // Load GUI behaviour settings from the respective layout files
        loadLayoutSettings();
    }

    private void initializeLayoutsDirectory() {
        if (!layoutsDir.exists()) {
            layoutsDir.mkdirs();
        }
        autoSaveLayoutFiles();
    }

    private void autoSaveLayoutFiles() {
        try {
            String[] layoutNames = new String[]{DEFAULT_LAYOUT, "DonutSMP", "DonutSMP_v2"};

            for (String layoutName : layoutNames) {
                File layoutDir = new File(layoutsDir, layoutName);
                if (!layoutDir.exists()) {
                    layoutDir.mkdirs();
                }

                // Save storage GUI layout
                File storageFile = new File(layoutDir, STORAGE_GUI_FILE);
                String storageResourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + STORAGE_GUI_FILE;

                if (!storageFile.exists()) {
                    try {
                        plugin.saveResource(storageResourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save storage layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }

                // Save main GUI layout
                File mainFile = new File(layoutDir, MAIN_GUI_FILE);
                String mainResourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + MAIN_GUI_FILE;

                if (!mainFile.exists()) {
                    try {
                        plugin.saveResource(mainResourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save main layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }

                // Save sell confirm GUI layout
                File sellConfirmFile = new File(layoutDir, SELL_CONFIRM_GUI_FILE);
                String sellConfirmResourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + SELL_CONFIRM_GUI_FILE;

                if (!sellConfirmFile.exists()) {
                    try {
                        plugin.saveResource(sellConfirmResourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save sell confirm layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to auto-save layout files", e);
        }
    }

    private void loadLayoutSettings() {
        // Read skip_main_gui from the current layout's main_gui.yml
        File mainFile = resolveLayoutFile(MAIN_GUI_FILE);
        this.skipMainGui = false;
        if (mainFile != null) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(mainFile);
                this.skipMainGui = config.getBoolean("skip_main_gui", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to read skip_main_gui from main_gui.yml, using default (false): " + e.getMessage(), e);
            }
        }

        // Read skip_sell_confirmation from the current layout's sell_confirm_gui.yml
        File sellConfirmFile = resolveLayoutFile(SELL_CONFIRM_GUI_FILE);
        this.skipSellConfirmation = false;
        if (sellConfirmFile != null) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(sellConfirmFile);
                this.skipSellConfirmation = config.getBoolean("skip_sell_confirmation", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to read skip_sell_confirmation from sell_confirm_gui.yml, using default (false): " + e.getMessage(), e);
            }
        }
    }

    /**
     * Resolve a layout file for the current layout, falling back to the default layout.
     */
    private File resolveLayoutFile(String fileName) {
        File layoutFile = new File(new File(layoutsDir, currentLayout), fileName);
        if (layoutFile.exists()) {
            return layoutFile;
        }
        if (!currentLayout.equals(DEFAULT_LAYOUT)) {
            File defaultFile = new File(new File(layoutsDir, DEFAULT_LAYOUT), fileName);
            if (defaultFile.exists()) {
                return defaultFile;
            }
        }
        return null;
    }

    private GuiLayout loadCurrentStorageLayout() {
        return loadLayoutFromFile(STORAGE_GUI_FILE, "storage");
    }

    private GuiLayout loadCurrentMainLayout() {
        return loadLayoutFromFile(MAIN_GUI_FILE, "main");
    }

    private GuiLayout loadCurrentSellConfirmLayout() {
        return loadLayoutFromFile(SELL_CONFIRM_GUI_FILE, "sell_confirm");
    }

    private GuiLayout loadLayoutFromFile(String fileName, String layoutType) {
        File layoutDir = new File(layoutsDir, currentLayout);
        File layoutFile = new File(layoutDir, fileName);

        if (layoutFile.exists()) {
            GuiLayout layout = loadLayout(layoutFile, layoutType);
            if (layout != null) {
                return layout;
            }
        }

        if (!currentLayout.equals(DEFAULT_LAYOUT)) {
            plugin.getLogger().warning("Layout '" + currentLayout + "' not found. Attempting to use default layout.");
            File defaultLayoutDir = new File(layoutsDir, DEFAULT_LAYOUT);
            File defaultLayoutFile = new File(defaultLayoutDir, fileName);

            if (defaultLayoutFile.exists()) {
                GuiLayout defaultLayout = loadLayout(defaultLayoutFile, layoutType);
                if (defaultLayout != null) {
                    plugin.getLogger().info("Loaded default " + layoutType + " layout as fallback");
                    return defaultLayout;
                }
            }
        }

        plugin.getLogger().severe("No valid " + layoutType + " layout found! Creating empty layout as fallback.");
        return new GuiLayout();
    }

    private GuiLayout loadLayout(File file, String layoutType) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            GuiLayout layout = new GuiLayout();

            // OPTIMIZATION: Read buttons directly from root (no "buttons:" wrapper)
            // Support both old format (buttons.xxx) and new format (slot_X)
            Set<String> buttonKeys = config.getKeys(false);

            if (buttonKeys.isEmpty()) {
                plugin.getLogger().warning("No buttons found in GUI layout: " + file.getName());
                return layout;
            }

            for (String buttonKey : buttonKeys) {
                // Skip non-button keys (like comments)
                if (!buttonKey.startsWith("slot_")) {
                    continue;
                }

                if (!loadButton(config, layout, buttonKey, layoutType)) {
                    plugin.getLogger().warning("Failed to load button: " + buttonKey);
                }
            }


            return layout;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load " + layoutType + " layout from " + file.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean loadButton(FileConfiguration config, GuiLayout layout, String buttonKey, String layoutType) {
        // OPTIMIZATION: Parse slot from button key (slot_11, slot_14, etc.)
        int slot = parseSlotFromKey(buttonKey);
        if (slot == -1) {
            plugin.getLogger().warning("Invalid button key format: " + buttonKey + ". Expected format: slot_X or slot_X_name");
            return false;
        }

        // Read button config directly from root (no "buttons." prefix)
        if (!config.getBoolean(buttonKey + ".enabled", true)) {
            return false;
        }

        String materialName = config.getString(buttonKey + ".material", "STONE");
        String condition = config.getString(buttonKey + ".condition", null);
        boolean infoButton = config.getBoolean(buttonKey + ".info_button", false);
        String customTexture = config.getString(buttonKey + ".custom_texture", null);

        // Validate slot based on layout type
        if (!isValidSlot(slot, layoutType)) {
            plugin.getLogger().warning(String.format(
                    "Invalid slot %d for button %s in %s layout. Must be between %d and %d.",
                    slot, buttonKey, layoutType, getMinSlot(layoutType), getMaxSlot(layoutType)));
            return false;
        }

        // Check condition if present (OLD format)
        if (condition != null && !evaluateCondition(condition)) {
            return false;
        }

        Material material = parseMaterial(materialName, buttonKey);
        int actualSlot = calculateActualSlot(slot, layoutType);

        // OPTIMIZATION: Load actions with support for conditional "if" blocks
        Map<String, String> actions = new HashMap<>();

        // Check for NEW "if" conditional format first
        ConfigurationSection ifSection = config.getConfigurationSection(buttonKey + ".if");
        if (ifSection != null) {
            // NEW format: if: { sell_integration: { click: "action" }, no_sell_integration: { click: "action2" } }
            for (String conditionKey : ifSection.getKeys(false)) {
                if (evaluateCondition(conditionKey)) {
                    // This condition matches, load its actions
                    ConfigurationSection conditionActions = ifSection.getConfigurationSection(conditionKey);
                    if (conditionActions != null) {
                        // Load material override if present
                        String conditionalMaterial = conditionActions.getString("material");
                        if (conditionalMaterial != null) {
                            material = parseMaterial(conditionalMaterial, buttonKey);
                        }

                        // Load custom texture override if present
                        String conditionalCustomTexture = conditionActions.getString("custom_texture");
                        if (conditionalCustomTexture != null) {
                            customTexture = conditionalCustomTexture;
                        }

                        // Load all click actions from this condition
                        String[] clickTypes = {"click", "left_click", "right_click", "shift_left_click", "shift_right_click"};
                        for (String clickType : clickTypes) {
                            String action = conditionActions.getString(clickType);
                            if (action != null && !action.isEmpty()) {
                                actions.put(clickType, action);
                            }
                        }
                    }
                    break; // Only use first matching condition
                }
            }
        } else {
            // OLD format: Direct click actions at button level
            String[] clickTypes = {"click", "left_click", "right_click", "shift_left_click", "shift_right_click"};
            for (String clickType : clickTypes) {
                String action = config.getString(buttonKey + "." + clickType);
                if (action != null && !action.isEmpty()) {
                    actions.put(clickType, action);
                }
            }
        }

        GuiButton button = new GuiButton(buttonKey, actualSlot, material, true, condition, actions, infoButton, customTexture);
        layout.addButton(buttonKey, button);
        return true;
    }

    /**
     * Parse slot number from button key
     * OPTIMIZATION: Extract slot from key like "slot_11" -> 11 or "slot_14_shop" -> 14
     * @param buttonKey The button key (e.g., "slot_11", "slot_14_shop")
     * @return Slot number or -1 if invalid
     */
    private int parseSlotFromKey(String buttonKey) {
        if (!buttonKey.startsWith("slot_")) {
            return -1;
        }

        try {
            // Remove "slot_" prefix
            String slotPart = buttonKey.substring(5);

            // Handle keys like "slot_14_shop" - extract just the number part
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
            // Both main and sell_confirm use 1-based to 0-based conversion
            return slot - 1;
        }
    }

    private boolean evaluateCondition(String condition) {
        switch (condition) {
            case "sell_integration":
                return plugin.hasSellIntegration();
            case "no_sell_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown condition: " + condition);
                return true;
        }
    }

    private Material parseMaterial(String materialName, String buttonKey) {
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format(
                    "Invalid material %s for button %s. Using STONE instead.",
                    materialName, buttonKey));
            return Material.STONE;
        }
    }

    public GuiLayout getCurrentLayout() {
        return getCurrentStorageLayout();
    }

    public void reloadLayouts() {
        loadLayout();
    }
}