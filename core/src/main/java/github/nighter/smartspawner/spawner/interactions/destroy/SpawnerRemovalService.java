package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.data.SpawnerRemovalOptions;
import github.nighter.smartspawner.api.data.SpawnerRemovalResult;
import github.nighter.smartspawner.api.events.SpawnerDestroyEvent;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import github.nighter.smartspawner.spawner.utils.SpawnerLocationLockManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Central service for programmatic SmartSpawner cage removal.
 */
public class SpawnerRemovalService {

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerLocationLockManager locationLockManager;
    private final SpawnerGuiViewManager guiViewManager;
    private final SpawnerRangeChecker rangeChecker;
    private final SpawnerMenuAction menuAction;
    private final SpawnerSellManager sellManager;

    public SpawnerRemovalService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.locationLockManager = plugin.getSpawnerLocationLockManager();
        this.guiViewManager = plugin.getSpawnerGuiViewManager();
        this.rangeChecker = plugin.getRangeChecker();
        this.menuAction = plugin.getSpawnerMenuAction();
        this.sellManager = plugin.getSpawnerSellManager();
    }

    public SpawnerRemovalResult removeSpawner(SpawnerData spawner, SpawnerRemovalOptions options) {
        if (spawner == null) {
            return SpawnerRemovalResult.NOT_FOUND;
        }
        if (options == null) {
            return SpawnerRemovalResult.FAILED;
        }
        if (options.isSellAndClaimExp() && options.getPayoutPlayer() == null) {
            return SpawnerRemovalResult.FAILED;
        }

        Location location = spawner.getSpawnerLocation();
        if (location.getWorld() == null) {
            return SpawnerRemovalResult.FAILED;
        }

        SpawnerData currentSpawner = spawnerManager.getSpawnerByLocation(location);
        if (currentSpawner == null || !currentSpawner.getSpawnerId().equals(spawner.getSpawnerId())) {
            return SpawnerRemovalResult.NOT_FOUND;
        }

        if (!locationLockManager.tryLock(location)) {
            return SpawnerRemovalResult.LOCKED;
        }

        boolean lockReleased = false;
        Runnable releaseLock = () -> {
            if (!lockReleased) {
                locationLockManager.unlock(location);
                lockReleased = true;
            }
        };

        try {
            currentSpawner = spawnerManager.getSpawnerByLocation(location);
            if (currentSpawner == null) {
                return SpawnerRemovalResult.NOT_FOUND;
            }

            if (currentSpawner.isSelling()) {
                return SpawnerRemovalResult.LOCKED;
            }

            Player initiator = options.getInitiator();
            Player payoutPlayer = options.getPayoutPlayer();
            if (shouldFireDestroyEvent()) {
                SpawnerDestroyEvent event = new SpawnerDestroyEvent(
                        location.clone(),
                        currentSpawner.getStackSize(),
                        options.getReason(),
                        currentSpawner.getEntityType(),
                        initiator,
                        payoutPlayer
                );
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return SpawnerRemovalResult.CANCELLED;
                }
            }

            guiViewManager.closeAllViewersInventory(currentSpawner);
            Block block = location.getBlock();
            final SpawnerData spawnerForCleanup = currentSpawner;
            Runnable cleanup = () -> performCleanup(block, spawnerForCleanup);
            Runnable cleanupAndReleaseLock = () -> {
                try {
                    cleanup.run();
                } finally {
                    releaseLock.run();
                }
            };

            if (options.isSellAndClaimExp() && payoutPlayer != null) {
                boolean sellDeferred = maybeSellAndClaimExp(
                        payoutPlayer,
                        currentSpawner,
                        cleanupAndReleaseLock,
                        releaseLock
                );
                if (sellDeferred) {
                    lockReleased = true;
                    return SpawnerRemovalResult.SELL_PENDING;
                }
            }

            cleanupAndReleaseLock.run();
            lockReleased = true;
            return SpawnerRemovalResult.SUCCESS;
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to remove spawner " + spawner.getSpawnerId() + ": " + exception.getMessage());
            return SpawnerRemovalResult.FAILED;
        } finally {
            if (!lockReleased) {
                releaseLock.run();
            }
        }
    }

    /**
     * Internal cleanup used by player break flow. Public API callers should use {@link #removeSpawner}.
     */
    void performCleanup(Block block, SpawnerData spawner) {
        guiViewManager.closeAllViewersInventory(spawner);
        spawner.getSpawnerStop().set(true);

        if (block.getType() == Material.SPAWNER) {
            block.setType(Material.AIR);
        }

        String spawnerId = spawner.getSpawnerId();
        rangeChecker.deactivateSpawner(spawner);
        spawnerManager.removeSpawner(spawnerId);
        spawnerManager.markSpawnerDeleted(spawnerId);

        Location location = block.getLocation();
        locationLockManager.removeLock(location);
        cleanupAssociatedHopper(block);
    }

    private boolean maybeSellAndClaimExp(
            Player player,
            SpawnerData spawner,
            Runnable onComplete,
            Runnable onAbort
    ) {
        if (menuAction != null && spawner.getSpawnerExp() > 0) {
            menuAction.collectExpForPlayer(player, spawner);
        }

        if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
            return false;
        }

        if (spawner.getVirtualInventory().getUsedSlots() <= 0) {
            return false;
        }

        sellManager.sellAllItems(player, spawner, () -> {
            if (spawner.getVirtualInventory().getUsedSlots() > 0) {
                onAbort.run();
                return;
            }
            onComplete.run();
        });
        return true;
    }

    private void cleanupAssociatedHopper(Block block) {
        HopperService hopperService = plugin.getHopperService();
        if (hopperService == null) {
            return;
        }
        hopperService.getTracker().removeBelowSpawner(block);
    }

    private boolean shouldFireDestroyEvent() {
        return SpawnerDestroyEvent.getHandlerList().getRegisteredListeners().length > 0;
    }
}
