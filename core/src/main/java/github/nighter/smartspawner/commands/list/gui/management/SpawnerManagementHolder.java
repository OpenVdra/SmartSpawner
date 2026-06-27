package github.nighter.smartspawner.commands.list.gui.management;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@Getter
public class SpawnerManagementHolder implements InventoryHolder {
    private final String spawnerId;
    private final String worldName;
    private final int listPage;

    public SpawnerManagementHolder(String spawnerId, String worldName, int listPage) {
        this.spawnerId = spawnerId;
        this.worldName = worldName;
        this.listPage = listPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
