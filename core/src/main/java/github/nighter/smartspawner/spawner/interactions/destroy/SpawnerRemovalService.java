package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.adminstacker.AdminStackerHolder;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementHolder;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.utils.SpawnerLocationLockManager;
import github.nighter.smartspawner.utils.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerRemovalService {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerStorage spawnerStorage;
    private final SpawnerLocationLockManager locationLockManager;
    private final Set<String> pendingRemovalIds = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> pendingRemovalLocations = ConcurrentHashMap.newKeySet();

    public SpawnerRemovalService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerStorage = plugin.getSpawnerStorage();
        this.locationLockManager = plugin.getSpawnerLocationLockManager();
    }

    public CompletableFuture<Boolean> removeSpawner(SpawnerData spawner) {
        if (spawner == null || spawner.getSpawnerId() == null) {
            return CompletableFuture.completedFuture(false);
        }

        Location location = spawner.getSpawnerLocation();
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        BlockPos blockPos = new BlockPos(location);
        boolean previousStop = spawner.getSpawnerStop().get();

        if (!claimRemoval(spawner, blockPos)) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        World world = location.getWorld();
        int chunkX = blockPos.getChunkX();
        int chunkZ = blockPos.getChunkZ();

        Runnable removeOnLocationThread = () -> removeBlockAndFinalize(spawner, blockPos, previousStop, future);
        closeRelatedInventories(spawner);

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            Scheduler.runLocationTask(location, removeOnLocationThread);
            return future;
        }

        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Failed to async load chunk for spawner removal " +
                        spawner.getSpawnerId() + ": " + error.getMessage());
                Scheduler.runLocationTask(location, () -> failRemoval(spawner, blockPos, previousStop, future));
                return;
            }

            Scheduler.runLocationTask(location, removeOnLocationThread);
        });

        return future;
    }

    public boolean isRemovalPending(SpawnerData spawner) {
        return spawner != null && pendingRemovalIds.contains(spawner.getSpawnerId());
    }

    public boolean isRemovalPending(Location location) {
        return location != null && location.getWorld() != null && pendingRemovalLocations.contains(new BlockPos(location));
    }

    private boolean claimRemoval(SpawnerData spawner, BlockPos blockPos) {
        Location location = spawner.getSpawnerLocation();
        if (!locationLockManager.tryLock(location)) {
            return false;
        }

        try {
            if (spawner.isSelling()) {
                return false;
            }

            SpawnerData currentById = spawnerManager.getSpawnerById(spawner.getSpawnerId());
            SpawnerData currentByLocation = spawnerManager.getSpawnerByLocation(location);
            if (currentById != spawner || currentByLocation != spawner) {
                return false;
            }

            if (!pendingRemovalIds.add(spawner.getSpawnerId())) {
                return false;
            }

            if (!pendingRemovalLocations.add(blockPos)) {
                pendingRemovalIds.remove(spawner.getSpawnerId());
                return false;
            }

            spawner.getSpawnerStop().set(true);
            return true;
        } finally {
            locationLockManager.unlock(location);
        }
    }

    private void removeBlockAndFinalize(
            SpawnerData spawner,
            BlockPos blockPos,
            boolean previousStop,
            CompletableFuture<Boolean> future
    ) {
        if (!pendingRemovalIds.contains(spawner.getSpawnerId())) {
            completeFuture(future, false);
            return;
        }

        Location location = spawner.getSpawnerLocation();
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(blockPos.getChunkX(), blockPos.getChunkZ())) {
            failRemoval(spawner, blockPos, previousStop, future);
            return;
        }

        if (!locationLockManager.tryLock(location)) {
            failRemoval(spawner, blockPos, previousStop, future);
            return;
        }

        boolean removeLocationLock = false;
        try {
            SpawnerData currentSpawner = spawnerManager.getSpawnerById(spawner.getSpawnerId());
            if (currentSpawner != spawner) {
                completeFuture(future, false);
                return;
            }

            Block block = location.getBlock();
            if (plugin.getRangeChecker() != null) {
                plugin.getRangeChecker().deactivateSpawner(spawner);
            }

            cleanupAssociatedHopper(block);
            if (block.getType() == Material.SPAWNER) {
                block.setType(Material.AIR);
            }

            spawner.getSpawnerStop().set(true);
            spawnerManager.removeSpawner(spawner.getSpawnerId());
            spawnerStorage.markSpawnerDeleted(spawner.getSpawnerId());
            removeLocationLock = true;
            completeFuture(future, true);
        } finally {
            pendingRemovalIds.remove(spawner.getSpawnerId());
            pendingRemovalLocations.remove(blockPos);
            locationLockManager.unlock(location);
            if (removeLocationLock) {
                locationLockManager.removeLock(location);
            }
        }
    }

    private void failRemoval(
            SpawnerData spawner,
            BlockPos blockPos,
            boolean previousStop,
            CompletableFuture<Boolean> future
    ) {
        pendingRemovalIds.remove(spawner.getSpawnerId());
        pendingRemovalLocations.remove(blockPos);
        spawner.getSpawnerStop().set(previousStop);
        completeFuture(future, false);
    }

    private void closeRelatedInventories(SpawnerData spawner) {
        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawner);

        String spawnerId = spawner.getSpawnerId();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduler.runEntityTask(player, () -> closeManagementInventory(player, spawnerId));
        }
    }

    private void closeManagementInventory(Player player, String spawnerId) {
        if (!player.isOnline()) {
            return;
        }

        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory == null) {
            return;
        }

        Object holder = inventory.getHolder(false);
        if (holder instanceof SpawnerManagementHolder managementHolder &&
                spawnerId.equals(managementHolder.getSpawnerId())) {
            player.closeInventory();
            return;
        }

        if (holder instanceof AdminStackerHolder adminStackerHolder &&
                adminStackerHolder.getSpawnerData() != null &&
                spawnerId.equals(adminStackerHolder.getSpawnerData().getSpawnerId())) {
            player.closeInventory();
        }
    }

    private void cleanupAssociatedHopper(Block block) {
        HopperService hopperService = plugin.getHopperService();
        if (hopperService != null) {
            hopperService.getTracker().removeBelowSpawner(block);
        }
    }

    private void completeFuture(CompletableFuture<Boolean> future, boolean value) {
        if (!future.isDone()) {
            future.complete(value);
        }
    }
}
