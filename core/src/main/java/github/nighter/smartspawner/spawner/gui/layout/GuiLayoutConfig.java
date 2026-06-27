package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.SmartSpawnerAPIImpl;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.gui.ExternalGuiLayoutLoader;
import github.nighter.smartspawner.api.gui.GuiLayoutAdapter;
import github.nighter.smartspawner.api.gui.GuiLayoutData;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistryImpl;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import github.nighter.smartspawner.api.gui.SpawnerGuiLayoutProvider;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.updates.GuiLayoutUpdater;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.logging.Level;

public class GuiLayoutConfig {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String STORAGE_GUI_FILE = "storage_gui.yml";
    private static final String MAIN_GUI_FILE = "main_gui.yml";
    private static final String SELL_CONFIRM_GUI_FILE = "sell_confirm_gui.yml";
    private static final String DEFAULT_LAYOUT = "default";

    private final SmartSpawner plugin;
    private final File layoutsDir;
    private final GuiLayoutUpdater layoutUpdater;
    private final ExternalGuiLayoutLoader loader;
    private final GuiLayoutRegistryImpl registry;

    @Setter
    private volatile SpawnerGuiLayoutProvider provider;

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
    @Getter
    private String openSound;

    public GuiLayoutConfig(SmartSpawner plugin, ExternalGuiLayoutLoader loader,
                           GuiLayoutRegistryImpl registry) {
        this.plugin = plugin;
        this.layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        this.layoutUpdater = new GuiLayoutUpdater(plugin);
        this.loader = loader;
        this.registry = registry;
        loadLayout();
    }

    public void loadLayout() {
        this.currentLayout = plugin.getConfig().getString("gui_layout", DEFAULT_LAYOUT);

        // Creates missing layout files with a version header and updates existing ones.
        layoutUpdater.checkAndUpdateLayouts();

        this.currentStorageLayout = loadCurrentStorageLayout();
        this.currentMainLayout = loadCurrentMainLayout();
        this.currentSellConfirmLayout = loadCurrentSellConfirmLayout();

        // Load GUI behaviour settings from the respective layout files
        loadLayoutSettings();
    }

    private void loadLayoutSettings() {
        // Read skip_main_gui and open_sound from the current layout's main_gui.yml
        File mainFile = resolveLayoutFile(MAIN_GUI_FILE);
        this.skipMainGui = false;
        this.openSound = null;
        if (mainFile != null) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(mainFile);
                this.skipMainGui = config.getBoolean("skip_main_gui", false);
                
                // Read open_sound (uses Minecraft namespaced key format, e.g., "block.ender_chest.open")
                // Volume and pitch are hardcoded to 1.0f for performance and simplicity
                String soundName = config.getString("open_sound");
                if (soundName != null && !soundName.trim().isEmpty() && !soundName.equalsIgnoreCase("none")) {
                    this.openSound = soundName.trim();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to read settings from main_gui.yml, using defaults: " + e.getMessage(), e);
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
            GuiLayout layout = loader.loadLayout(layoutFile, layoutType);
            if (layout != null) {
                return layout;
            }
        }

        if (!currentLayout.equals(DEFAULT_LAYOUT)) {
            plugin.getLogger().warning("Layout '" + currentLayout + "' not found. Attempting to use default layout.");
            File defaultLayoutDir = new File(layoutsDir, DEFAULT_LAYOUT);
            File defaultLayoutFile = new File(defaultLayoutDir, fileName);

            if (defaultLayoutFile.exists()) {
                GuiLayout defaultLayout = loader.loadLayout(defaultLayoutFile, layoutType);
                if (defaultLayout != null) {
                    plugin.getLogger().info("Loaded default " + layoutType + " layout as fallback");
                    return defaultLayout;
                }
            }
        }

        plugin.getLogger().severe("No valid " + layoutType + " layout found! Creating empty layout as fallback.");
        return new GuiLayout();
    }

    /**
     * Returns the main GUI layout for the given spawner and player.
     * Resolution priority:
     * 1. Per-spawner provider (if registered and applicable)
     * 2. Global registered layout (if currentLayout matches a registered name)
     * 3. File-based layout (existing behavior - fallback)
     *
     * @param spawner the spawner data
     * @param player the player opening the GUI
     * @return the resolved main GUI layout
     */
    public GuiLayout getMainLayout(SpawnerData spawner, Player player) {
        GuiLayout providerLayout = getProviderLayout(spawner, player, GuiLayoutType.MAIN_GUI);
        if (providerLayout != null) return providerLayout;

        // 2. Global registry
        GuiLayoutRegistryImpl currentRegistry = this.registry;
        if (currentRegistry != null) {
            GuiLayout regLayout = currentRegistry.getRegisteredMainGui(currentLayout);
            if (regLayout != null) {
                return regLayout;
            }
        }
        // 3. Fallback to file
        return getCurrentMainLayout();
    }

    /**
     * Returns the storage GUI layout for the given spawner and player.
     */
    public GuiLayout getStorageLayout(SpawnerData spawner, Player player) {
        GuiLayout providerLayout = getProviderLayout(spawner, player, GuiLayoutType.STORAGE_GUI);
        if (providerLayout != null) return providerLayout;

        GuiLayoutRegistryImpl currentRegistry = this.registry;
        if (currentRegistry != null) {
            GuiLayout regLayout = currentRegistry.getRegisteredStorageGui(currentLayout);
            if (regLayout != null) {
                return regLayout;
            }
        }
        return getCurrentStorageLayout();
    }

    /**
     * Returns the sell confirm GUI layout for the given spawner and player.
     */
    public GuiLayout getSellConfirmLayout(SpawnerData spawner, Player player) {
        GuiLayout providerLayout = getProviderLayout(spawner, player, GuiLayoutType.SELL_CONFIRM_GUI);
        if (providerLayout != null) return providerLayout;

        GuiLayoutRegistryImpl currentRegistry = this.registry;
        if (currentRegistry != null) {
            GuiLayout regLayout = currentRegistry.getRegisteredSellConfirmGui(currentLayout);
            if (regLayout != null) {
                return regLayout;
            }
        }
        return getCurrentSellConfirmLayout();
    }

    private GuiLayout getProviderLayout(SpawnerData spawner, Player player, GuiLayoutType type) {
        SpawnerGuiLayoutProvider currentProvider = this.provider;
        if (currentProvider == null) {
            return null;
        }

        try {
            SpawnerDataDTO dto = SmartSpawnerAPIImpl.convertToDTO(spawner);
            GuiLayoutData data = currentProvider.getLayout(dto, player, type);
            return GuiLayoutAdapter.toCoreLayout(data, type);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "SpawnerGuiLayoutProvider '" + getProviderName(currentProvider)
                            + "' failed to provide " + type, e);
            return null;
        }
    }

    private String getProviderName(SpawnerGuiLayoutProvider currentProvider) {
        try {
            String name = currentProvider.getProviderName();
            return name != null && !name.isBlank() ? name : currentProvider.getClass().getName();
        } catch (Exception ignored) {
            return currentProvider.getClass().getName();
        }
    }

    public GuiLayout getCurrentLayout() {
        return getCurrentStorageLayout();
    }

    public void reloadLayouts() {
        loadLayout();
    }
}
