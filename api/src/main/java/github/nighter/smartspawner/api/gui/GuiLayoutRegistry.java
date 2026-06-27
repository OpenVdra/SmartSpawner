package github.nighter.smartspawner.api.gui;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * Registry for external plugins to register custom GUI layouts.
 * Obtain an instance via {@link github.nighter.smartspawner.api.SmartSpawnerAPI#getLayoutRegistry()}.
 *
 * <p>Registered layouts can be selected in the main {@code config.yml} using the
 * {@code gui_layout} setting, just like built-in file-based layouts.</p>
 */
public interface GuiLayoutRegistry {

    /**
     * Registers a programmatic layout (built via {@link GuiLayoutBuilder}).
     * Any of the layout parameters may be null if the external plugin does not wish
     * to override that specific GUI type.
     *
     * @param layoutName the unique layout name (used in config.yml {@code gui_layout})
     * @param mainGui the main GUI layout, or null
     * @param storageGui the storage GUI layout, or null
     * @param sellConfirmGui the sell confirmation GUI layout, or null
     * @return true if registered successfully, false if name already taken
     */
    boolean registerLayout(String layoutName,
                           @Nullable GuiLayoutData mainGui,
                           @Nullable GuiLayoutData storageGui,
                           @Nullable GuiLayoutData sellConfirmGui);

    /**
     * Registers a layout from YAML files provided by an external plugin.
     * The YAML format must match the built-in SmartSpawner layout files.
     *
     * @param layoutName the unique layout name
     * @param mainGuiFile the main GUI YAML file, or null
     * @param storageGuiFile the storage GUI YAML file, or null
     * @param sellConfirmGuiFile the sell confirm GUI YAML file, or null
     * @return true if registered successfully, false if name already taken or files invalid
     */
    boolean registerLayoutFromYaml(String layoutName,
                                   @Nullable File mainGuiFile,
                                   @Nullable File storageGuiFile,
                                   @Nullable File sellConfirmGuiFile);

    /**
     * Unregisters a previously registered layout.
     *
     * @param layoutName the layout name to unregister
     * @return true if unregistered, false if not found
     */
    boolean unregisterLayout(String layoutName);

    /**
     * Checks whether a layout name is already registered.
     *
     * @param layoutName the layout name to check
     * @return true if registered
     */
    boolean isRegistered(String layoutName);

    /**
     * Returns a set of all registered layout names.
     *
     * @return set of registered layout names
     */
    Set<String> getRegisteredLayouts();
}
