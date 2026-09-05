package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.StorageView;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Reconciles a native item take-out that Bukkit has already applied to the open storage inventory but
 * that the count-map does not yet know about. The last painted image ({@link StorageView}) is the
 * baseline: whatever disappeared from a slot is debited from that exact cell. Safe only under the
 * single-viewer lock, where no other Bukkit inventory of this spawner exists.
 *
 * @see StoragePageEditor#updatePageAfterRemoval for the post-removal bookkeeping this runs.
 */
class StorageReconciler {
    private static final int STORAGE_SLOTS = 45;

    private final StoragePageEditor pageEditor;

    StorageReconciler(StoragePageEditor pageEditor) {
        this.pageEditor = pageEditor;
    }

    /**
     * Arms the holder and schedules the backstop debit for the next tick, since Bukkit applies the
     * click result only after the LOWEST-priority click listener returns. Until that debit lands the
     * last painted image is the sole record of what was taken, and it survives neither a repaint nor a
     * close, so every path doing either calls {@link #flushPendingReconcile} first.
     */
    void scheduleReconcile(Player player, StoragePageHolder holder) {
        holder.armReconcile();
        Scheduler.runEntityTaskLater(player, () -> reconcileNativeTake(player, holder), 1L);
    }

    private void reconcileNativeTake(Player player, StoragePageHolder holder) {
        if (!holder.isPendingReconcile()) {
            return;
        }
        // The close and quit hooks flush while the inventory is still readable, so reaching either
        // branch below still armed means there is nothing left to diff against.
        if (!player.isOnline()) {
            holder.clearReconcile();
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder current) || current != holder) {
            holder.clearReconcile();
            return;
        }

        if (reconcile(player, inventory, holder, true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
        }
    }

    /**
     * Settles an outstanding native take, if any. Call before repainting the storage GUI, closing it,
     * or reading the count-map as authoritative; pass {@code repaint} false when the inventory is
     * being torn down.
     */
    void flushPendingReconcile(Player player, Inventory inventory, boolean repaint) {
        if (inventory == null
                || !(inventory.getHolder(false) instanceof StoragePageHolder holder)
                || !holder.isPendingReconcile()) {
            return;
        }
        // The isPendingReconcile() check above is only a cheap pre-filter; reconcile() re-checks it
        // atomically via claimReconcile(), so a concurrent settle path cannot make us double-debit.
        reconcile(player, inventory, holder, repaint);
    }

    /**
     * Debits the count-map for a native take Bukkit already applied. The last painted image is the
     * baseline: whatever disappeared from a Bukkit slot is taken from that exact frozen cell, and
     * {@link SpawnerData#takeItemFromCell} clamps to the live amount so loot that arrived meanwhile
     * is kept. Safe only under the single-viewer lock, where no other Bukkit inventory of this
     * spawner exists.
     */
    private boolean reconcile(Player player, Inventory inventory, StoragePageHolder holder,
                              boolean repaint) {
        // Claim atomically, which also disarms: only one racing settle path wins, and updatePageAfterRemoval
        // repaints, so a re-entrant flush reached from there must not diff against the image it is replacing.
        if (!holder.claimReconcile()) {
            return false;
        }

        SpawnerData spawner = holder.getSpawnerData();
        int page = holder.getCurrentPage();
        int startSlot = (page - 1) * STORAGE_SLOTS;
        int usable = SpawnerStorageUI.usableItemSlots(spawner, page);
        StorageView view = holder.getView();

        boolean anyRemoved = false;
        for (int i = 0; i < STORAGE_SLOTS && i < usable; i++) {
            ItemStack painted = view.get(i);
            if (painted == null || painted.getType() == Material.AIR) {
                continue;
            }
            ItemStack now = inventory.getItem(i);
            int nowAmount = (now != null && now.isSimilar(painted)) ? now.getAmount() : 0;
            int removed = painted.getAmount() - nowAmount;
            if (removed > 0) {
                spawner.takeItemFromCell(startSlot + i, removed);
                anyRemoved = true;
            }
        }

        if (anyRemoved) {
            pageEditor.updatePageAfterRemoval(player, inventory, spawner, holder, repaint);
        }
        return anyRemoved;
    }
}
