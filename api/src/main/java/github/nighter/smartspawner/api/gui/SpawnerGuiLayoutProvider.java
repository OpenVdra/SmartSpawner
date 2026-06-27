package github.nighter.smartspawner.api.gui;

import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import org.bukkit.entity.Player;

/**
 * Interface for plugins that want to provide per-spawner GUI layout overrides.
 * Register an instance of this provider via
 * {@link github.nighter.smartspawner.api.SmartSpawnerAPI#setSpawnerLayoutProvider(SpawnerGuiLayoutProvider)}.
 *
 * <p>The provider is called every time a GUI is opened, allowing dynamic layout
 * selection based on the spawner, player, or any external condition.</p>
 */
public interface SpawnerGuiLayoutProvider {

    /**
     * Returns a custom layout for the given spawner and player, or {@code null}
     * to use the default layout resolution (global registry → file-based).
     *
     * @param spawner the spawner data (read-only)
     * @param player the player opening the GUI
     * @param type the type of GUI being opened
     * @return a custom layout, or null to use the default
     */
    GuiLayoutData getLayout(SpawnerDataDTO spawner, Player player, GuiLayoutType type);

    /**
     * Returns a human-readable name for this provider, used for logging/debugging.
     *
     * @return the provider name
     */
    String getProviderName();
}
