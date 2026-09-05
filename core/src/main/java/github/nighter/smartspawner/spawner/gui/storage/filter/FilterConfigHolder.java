package github.nighter.smartspawner.spawner.gui.storage.filter;

import github.nighter.smartspawner.spawner.model.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class FilterConfigHolder implements InventoryHolder {
    private final SpawnerData spawnerData;

    public FilterConfigHolder(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

}