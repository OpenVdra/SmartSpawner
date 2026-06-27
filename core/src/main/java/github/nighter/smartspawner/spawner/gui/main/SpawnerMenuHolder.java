package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.spawner.gui.SpawnerHolder;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerMenuHolder implements InventoryHolder, SpawnerHolder {
    @Getter
    private final SpawnerData spawnerData;
    @Getter
    private final GuiLayout layout;
    @Getter
    private final GuiButton storageButton;
    @Getter
    private final GuiButton expButton;
    @Getter
    private final GuiButton infoButton;

    public SpawnerMenuHolder(SpawnerData spawnerData, GuiLayout layout) {
        this.spawnerData = spawnerData;
        this.layout = layout;

        GuiButton storage = null;
        GuiButton exp = null;
        GuiButton info = null;
        if (layout != null) {
            for (GuiButton button : layout.getAllButtons().values()) {
                if (!button.isEnabled()) {
                    continue;
                }
                if (info == null && button.isInfoButton()) {
                    info = button;
                }
                if (button.getActions() == null) {
                    continue;
                }
                if (storage == null && button.getActions().containsValue("open_storage")) {
                    storage = button;
                }
                if (exp == null && button.getActions().containsValue("collect_exp")) {
                    exp = button;
                }
            }
        }
        this.storageButton = storage;
        this.expButton = exp;
        this.infoButton = info;
    }

    @Override
    public Inventory getInventory() {
        return null; // Required by interface
    }

}
