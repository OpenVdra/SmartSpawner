package github.nighter.smartspawner.commands.clear;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@NullMarked
public class ClearGhostSpawnersSubCommand extends BaseSubCommand {

    public ClearGhostSpawnersSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "ghost_spawners";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.clear";
    }

    @Override
    public String getDescription() {
        return "Check and remove all ghost spawners asynchronously";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        // Notify that the check is starting
        plugin.getMessageService().sendMessage(sender, "clear.ghost_check_start");

        // Get all spawners first
        List<SpawnerData> allSpawners = plugin.getSpawnerManager().getAllSpawners();
        
        // Track how many spawners are being checked using thread-safe counter
        final AtomicInteger removedCount = new AtomicInteger(0);
        
        // Check each spawner on its location thread for Folia compatibility
        for (SpawnerData spawner : allSpawners) {
            org.bukkit.Location loc = spawner.getSpawnerLocation();
            if (loc != null && loc.getWorld() != null) {
                Scheduler.runLocationTask(loc, () -> {
                    if (plugin.getSpawnerManager().isGhostSpawner(spawner)) {
                        plugin.getSpawnerManager().removeGhostSpawner(spawner.getSpawnerId());
                        removedCount.incrementAndGet();
                    }
                });
            }
        }
        
        // Schedule a delayed message to report results (give time for checks to complete)
        Scheduler.runTaskLater(() -> {
            int count = removedCount.get();
            if (count > 0) {
                plugin.getMessageService().sendMessage(sender, "clear.ghost_cleared",
                    java.util.Map.of("count", String.valueOf(count)));
            } else {
                plugin.getMessageService().sendMessage(sender, "clear.ghost_none_found");
            }
        }, 100L); // Wait 5 seconds (100 ticks) for checks to complete

        return 1;
    }
}
