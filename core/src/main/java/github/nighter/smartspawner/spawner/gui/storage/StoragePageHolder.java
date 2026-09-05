package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.spawner.gui.SpawnerHolder;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class StoragePageHolder implements InventoryHolder, SpawnerHolder {
    @Getter
    private final SpawnerData spawnerData;
    @Getter
    private int currentPage;
    @Getter
    private int totalPages;
    @Getter
    private int oldUsedSlots;
    @Getter
    private final GuiLayout layout;
    // Per-open render cache for diff rendering, owned by this open inventory.
    @Getter
    private final StorageView view = new StorageView();

    // Make these public static final for better performance and accessibility
    public static final int ROWS_PER_PAGE = 5;
    public static final int MAX_ITEMS_PER_PAGE = 45; // Pre-calculated value
    private Inventory inventory;

    public StoragePageHolder(SpawnerData spawnerData, int currentPage, int totalPages, GuiLayout layout) {
        this.spawnerData = spawnerData;
        this.currentPage = Math.max(1, Math.min(currentPage, totalPages));
        this.totalPages = Math.max(1, totalPages);
        this.oldUsedSlots = spawnerData.getVirtualInventory().getDisplaySlotCount();
        this.layout = layout;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(1, Math.min(page, totalPages));
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
    }

    public void updateOldUsedSlots() {
        this.oldUsedSlots = spawnerData.getVirtualInventory().getDisplaySlotCount();
    }
}
