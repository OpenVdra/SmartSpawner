package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.spawner.gui.SpawnerHolder;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

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
    // AtomicBoolean, not a plain field: on Folia the settle paths (scheduled backstop, close, quit,
    // and the version refresh) can be dispatched from different region threads, so the claim that
    // grants the right to debit must be atomic or two of them could both debit the same take.
    private final AtomicBoolean pendingReconcile = new AtomicBoolean(false);

    /**
     * Canonical storage page-count formula, shared by every page-count site so they cannot diverge.
     * {@code displaySlots} must be the frozen display slot count ({@code getDisplaySlotCount()}), not
     * the packed used-slot count, or navigation stops matching what is laid out and rendered.
     */
    public static int totalPagesFor(int displaySlots) {
        return displaySlots <= 0 ? 1 : (int) Math.ceil((double) displaySlots / MAX_ITEMS_PER_PAGE);
    }

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

    /** @return true if a native take is applied to the Bukkit inventory but not yet debited. */
    public boolean isPendingReconcile() {
        return pendingReconcile.get();
    }

    /** Arms the flag when a native take is handed to the player and scheduled for settling. */
    public void armReconcile() {
        pendingReconcile.set(true);
    }

    /** Clears the flag without reconciling, for the give-up paths (offline, inventory swapped out). */
    public void clearReconcile() {
        pendingReconcile.set(false);
    }

    /**
     * Atomically claims an outstanding reconcile. Succeeds for exactly one caller per arm, so that of
     * several racing settle paths (scheduled backstop, close, quit, version refresh) only the winner
     * performs the debit; the others see it already claimed and do nothing.
     */
    public boolean claimReconcile() {
        return pendingReconcile.compareAndSet(true, false);
    }
}
