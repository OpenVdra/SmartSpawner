package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.spawner.gui.SpawnerHolder;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

public class StoragePageHolder implements InventoryHolder, SpawnerHolder {
    public static final int MAX_ITEMS_PER_PAGE = 45;

    @Getter private final SpawnerData spawnerData;
    @Getter private int currentPage;
    @Getter private int totalPages;
    @Getter private int oldUsedSlots;
    @Getter private final GuiLayout layout;

    // Per-open render cache for diff rendering, owned by this open inventory.
    @Getter private final StorageView view = new StorageView();

    // Set while a native take-out is applied to the Bukkit inventory but not yet debited from the
    // count-map. The debit diffs view against the live slots, so any repaint or close must settle it.
    @Getter @Setter private boolean pendingReconcile;

    public StoragePageHolder(SpawnerData spawnerData, int currentPage, int totalPages, GuiLayout layout) {
        this.spawnerData = spawnerData;
        // Normalise the bound before clamping against it, so a caller passing 0 or the -1 sentinel
        // lands on page 1 instead of tripping the clamp's min > max check.
        this.totalPages = Math.max(1, totalPages);
        this.currentPage = Math.clamp(currentPage, 1, this.totalPages);
        this.oldUsedSlots = spawnerData.getVirtualInventory().getDisplaySlotCount();
        this.layout = layout;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null; // Required by interface
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.clamp(page, 1, totalPages);
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
    }

    public void updateOldUsedSlots() {
        this.oldUsedSlots = spawnerData.getVirtualInventory().getDisplaySlotCount();
    }
}
