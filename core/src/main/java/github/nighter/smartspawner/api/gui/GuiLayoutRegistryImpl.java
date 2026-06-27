package github.nighter.smartspawner.api.gui;

import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link GuiLayoutRegistry} for registering external GUI layouts.
 */
public class GuiLayoutRegistryImpl implements GuiLayoutRegistry {

    private static final String DEFAULT_LAYOUT = "default";

    private final ConcurrentHashMap<String, RegisteredExternalLayout> registeredLayouts = new ConcurrentHashMap<>();
    private final ExternalGuiLayoutLoader loader;
    private final Logger logger;

    public GuiLayoutRegistryImpl(ExternalGuiLayoutLoader loader, Logger logger) {
        this.loader = loader;
        this.logger = logger;
    }

    @Override
    public boolean registerLayout(String layoutName,
                                  @Nullable GuiLayoutData mainGui,
                                  @Nullable GuiLayoutData storageGui,
                                  @Nullable GuiLayoutData sellConfirmGui) {
        String normalizedName = normalizeName(layoutName);
        if (normalizedName == null || (mainGui == null && storageGui == null && sellConfirmGui == null)) {
            return false;
        }
        if (DEFAULT_LAYOUT.equals(normalizedName) && logger != null) {
            logger.warning("An external plugin registered a layout named 'default'. This shadows the built-in default layout from gui_layouts/default/.");
        }
        try {
            RegisteredExternalLayout reg = new RegisteredExternalLayout(
                    GuiLayoutAdapter.toCoreLayout(mainGui, GuiLayoutType.MAIN_GUI),
                    GuiLayoutAdapter.toCoreLayout(storageGui, GuiLayoutType.STORAGE_GUI),
                    GuiLayoutAdapter.toCoreLayout(sellConfirmGui, GuiLayoutType.SELL_CONFIRM_GUI)
            );
            return registeredLayouts.putIfAbsent(normalizedName, reg) == null;
        } catch (IllegalArgumentException e) {
            logInvalidLayout(normalizedName, e);
            return false;
        }
    }

    @Override
    public boolean registerLayoutFromYaml(String layoutName,
                                          @Nullable File mainGuiFile,
                                          @Nullable File storageGuiFile,
                                          @Nullable File sellConfirmGuiFile) {
        String normalizedName = normalizeName(layoutName);
        if (normalizedName == null || !areValidFiles(mainGuiFile, storageGuiFile, sellConfirmGuiFile)) {
            return false;
        }
        if (registeredLayouts.containsKey(normalizedName)) {
            return false;
        }
        if (DEFAULT_LAYOUT.equals(normalizedName) && logger != null) {
            logger.warning("An external plugin registered a layout named 'default' from YAML. This shadows the built-in default layout from gui_layouts/default/.");
        }

        GuiLayout mainGui = mainGuiFile != null && mainGuiFile.exists() ? loader.loadLayout(mainGuiFile, "main") : null;
        GuiLayout storageGui = storageGuiFile != null && storageGuiFile.exists() ? loader.loadLayout(storageGuiFile, "storage") : null;
        GuiLayout sellConfirmGui = sellConfirmGuiFile != null && sellConfirmGuiFile.exists() ? loader.loadLayout(sellConfirmGuiFile, "sell_confirm") : null;

        if ((mainGuiFile != null && mainGui == null)
                || (storageGuiFile != null && storageGui == null)
                || (sellConfirmGuiFile != null && sellConfirmGui == null)) {
            return false;
        }

        if (mainGui == null && storageGui == null && sellConfirmGui == null) {
            return false;
        }

        RegisteredExternalLayout reg = new RegisteredExternalLayout(mainGui, storageGui, sellConfirmGui);
        return registeredLayouts.putIfAbsent(normalizedName, reg) == null;
    }

    @Override
    public boolean unregisterLayout(String layoutName) {
        String normalizedName = normalizeName(layoutName);
        return normalizedName != null && registeredLayouts.remove(normalizedName) != null;
    }

    @Override
    public boolean isRegistered(String layoutName) {
        String normalizedName = normalizeName(layoutName);
        return normalizedName != null && registeredLayouts.containsKey(normalizedName);
    }

    @Override
    public Set<String> getRegisteredLayouts() {
        return Collections.unmodifiableSet(registeredLayouts.keySet());
    }

    /**
     * Gets a registered layout by name.
     *
     * @param layoutName the layout name
     * @return the registered layout, or null
     */
    RegisteredExternalLayout getRegisteredLayout(String layoutName) {
        String normalizedName = normalizeName(layoutName);
        return normalizedName != null ? registeredLayouts.get(normalizedName) : null;
    }

    /**
     * Gets the registered main GUI layout for a layout name.
     * @param layoutName the layout name
     * @return the main GUI layout, or null
     */
    public GuiLayout getRegisteredMainGui(String layoutName) {
        RegisteredExternalLayout reg = getRegisteredLayout(layoutName);
        return reg != null ? reg.getMainGui() : null;
    }

    /**
     * Gets the registered storage GUI layout for a layout name.
     * @param layoutName the layout name
     * @return the storage GUI layout, or null
     */
    public GuiLayout getRegisteredStorageGui(String layoutName) {
        RegisteredExternalLayout reg = getRegisteredLayout(layoutName);
        return reg != null ? reg.getStorageGui() : null;
    }

    /**
     * Gets the registered sell confirm GUI layout for a layout name.
     * @param layoutName the layout name
     * @return the sell confirm GUI layout, or null
     */
    public GuiLayout getRegisteredSellConfirmGui(String layoutName) {
        RegisteredExternalLayout reg = getRegisteredLayout(layoutName);
        return reg != null ? reg.getSellConfirmGui() : null;
    }

    private String normalizeName(String layoutName) {
        if (layoutName == null || layoutName.isBlank()) {
            return null;
        }
        return layoutName.trim();
    }

    private boolean areValidFiles(File... files) {
        boolean hasFile = false;
        for (File file : files) {
            if (file == null) {
                continue;
            }
            hasFile = true;
            if (!file.isFile()) {
                return false;
            }
        }
        return hasFile;
    }

    private void logInvalidLayout(String layoutName, IllegalArgumentException exception) {
        if (logger != null) {
            logger.log(Level.WARNING,
                    "Failed to register GUI layout '" + layoutName + "': " + exception.getMessage());
        }
    }
}
