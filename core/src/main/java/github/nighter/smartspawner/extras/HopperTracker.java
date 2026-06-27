package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.BlockPos;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class HopperTracker implements Listener {

    private final SmartSpawner plugin;
    private final HopperRegistry registry;

    public HopperTracker(SmartSpawner plugin, HopperRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void tryAdd(Block hopper) {
        if (hopper.getType() != Material.HOPPER) return;
        if (hopper.getRelative(BlockFace.UP).getType() != Material.SPAWNER) return;

        registry.add(new BlockPos(hopper.getLocation()));
    }

    public void removeBelowSpawner(Block spawner) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        Block hopper = spawner.getRelative(BlockFace.DOWN);
        if (hopper.getType() == Material.HOPPER) {
            registry.remove(new BlockPos(hopper.getLocation()));
        }
    }

    public void scanLoadedWorlds() {
        for (var world : plugin.getServer().getWorlds()) {
            scanLoadedChunksInWorld(world);
        }
    }

    public void scanLoadedChunksInWorld(World world) {
        for (Chunk loadedChunk : world.getLoadedChunks()) {
            int x = loadedChunk.getX();
            int z = loadedChunk.getZ();

            Scheduler.runChunkTask(world, x, z, () -> scanChunkInternal(world, x, z));
        }
    }

    private void scanChunkInternal(org.bukkit.World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        scanChunk(chunk);
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(
                b -> b.getType() == Material.HOPPER, false)) {

            tryAdd(state.getBlock());
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        scanLoadedChunksInWorld(e.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        registry.removeWorld(e.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        scanChunk(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        Chunk chunk = e.getChunk();
        registry.removeChunk(chunk.getWorld().getUID(),
                chunk.getX(),
                chunk.getZ()
        );
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        if (e.getBlockPlaced().getType() == Material.HOPPER) {
            tryAdd(e.getBlockPlaced());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        if (e.getBlock().getType() == Material.HOPPER) {
            registry.remove(new BlockPos(e.getBlock().getLocation()));
        }
    }
}
