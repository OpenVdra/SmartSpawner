package github.nighter.smartspawner.extras;


import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.BlockPos;
import github.nighter.smartspawner.utils.ChunkUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.HandlerList;

public class HopperService {

    private final SmartSpawner plugin;
    @Getter
    private final HopperRegistry registry;
    private final HopperTransfer transfer;
    @Getter
    private final HopperTracker tracker;
    private final Scheduler.Task task;

    public HopperService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.registry = new HopperRegistry();
        this.transfer = new HopperTransfer(plugin);
        this.tracker = new HopperTracker(plugin, registry);
        this.tracker.scanLoadedWorlds();

        long delay = plugin.getTimeFromConfig("hopper.check_delay", "3s");

        this.task = Scheduler.runTaskTimer(this::tick, 40L, delay);
    }

    private void tick() {
        if (!plugin.getHopperConfig().isHopperEnabled()) return;

        registry.forEachChunk((worldId, chunkKey) -> {

            World world = Bukkit.getWorld(worldId);
            if (world == null) return;

            int chunkX = ChunkUtil.getChunkX(chunkKey);
            int chunkZ = ChunkUtil.getChunkZ(chunkKey);

            Scheduler.runChunkTask(world, chunkX, chunkZ, () -> {
                for (BlockPos pos : registry.getChunkHoppers(worldId, chunkKey)) {
                    transfer.process(pos);
                }
            });
        });
    }

    /**
     * Must be called in plugin onDisable()
     */
    public void cleanup() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }

        if (tracker != null) {
            HandlerList.unregisterAll(tracker);
        }
    }

}
