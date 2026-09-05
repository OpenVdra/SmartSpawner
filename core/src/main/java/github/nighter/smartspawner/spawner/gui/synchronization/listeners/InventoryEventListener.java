package github.nighter.smartspawner.spawner.gui.synchronization.listeners;

import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;
import java.util.UUID;

/**
 * Listener for inventory-related events.
 * Tracks when players open and close spawner GUIs.
 */
public class InventoryEventListener implements Listener {

    private final ViewerTrackingManager viewerTrackingManager;
    private final Runnable onViewerAdded;
    private final MessageService messageService;
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;

    public InventoryEventListener(ViewerTrackingManager viewerTrackingManager, Runnable onViewerAdded,
                                  MessageService messageService) {
        this.viewerTrackingManager = viewerTrackingManager;
        this.onViewerAdded = onViewerAdded;
        this.messageService = messageService;
        this.validHolderTypes = Set.of(
                SpawnerMenuHolder.class,
                StoragePageHolder.class,
                FilterConfigHolder.class
        );
    }

    /**
     * Single-viewer gate for storage GUIs. Runs before the tracking handler (which is at MONITOR):
     * if another player already has this spawner's storage open, the open is cancelled and the
     * player is told to wait. This is what keeps native item interaction dupe-safe, since only one
     * Bukkit inventory ever exists per spawner storage at a time.
     *
     * <p>The gate looks only for a different UUID already viewing this spawner's storage, so it is
     * independent of whether the opening player is tracked yet. A player's own reopen (after a sell
     * or filter round-trip) closes the previous storage first, so their UUID is no longer present
     * and the reopen passes.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStorageOpenGate(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!(holder instanceof StoragePageHolder storageHolder)) {
            return;
        }

        if (viewerTrackingManager.isStorageViewedByOther(storageHolder.getSpawnerData(), player.getUniqueId())) {
            event.setCancelled(true);
            messageService.sendMessage(player, "storage_in_use");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!isValidHolder(holder)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        SpawnerData spawnerData = null;
        ViewerTrackingManager.ViewerType viewerType = null;

        if (holder instanceof SpawnerMenuHolder spawnerHolder) {
            spawnerData = spawnerHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.MAIN_MENU;
        } else if (holder instanceof StoragePageHolder storageHolder) {
            spawnerData = storageHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.STORAGE;
        } else if (holder instanceof FilterConfigHolder filterHolder) {
            spawnerData = filterHolder.getSpawnerData();
            viewerType = ViewerTrackingManager.ViewerType.FILTER;
        }

        if (spawnerData != null && viewerType != null) {
            // Record the interacting player immediately on open, not on close.
            // This avoids data loss when a player disconnects while the GUI is open,
            // since InventoryCloseEvent is not guaranteed to fire before PlayerQuitEvent.
            spawnerData.updateLastInteractedPlayer(player.getName());

            viewerTrackingManager.trackViewer(playerId, spawnerData, viewerType);
            onViewerAdded.run(); // Trigger update task start if needed
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Read the closing holder before untracking so we can tell when the LAST storage viewer of a
        // spawner leaves and start the reorder grace window for it.
        InventoryHolder holder = event.getInventory().getHolder(false);
        viewerTrackingManager.untrackViewer(player.getUniqueId());

        if (holder instanceof StoragePageHolder storageHolder) {
            SpawnerData spawner = storageHolder.getSpawnerData();
            if (!viewerTrackingManager.hasStorageViewers(spawner)) {
                spawner.markStorageEmptyNow();
            }
        }
    }

    /**
     * Validates if the inventory holder is a supported spawner GUI type.
     */
    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }
}
