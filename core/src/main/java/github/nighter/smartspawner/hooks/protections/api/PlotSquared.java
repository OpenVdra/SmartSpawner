package github.nighter.smartspawner.hooks.protections.api;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.events.PlotDeleteEvent;
import com.plotsquared.core.events.Result;
import com.plotsquared.core.plot.Plot;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;

public class PlotSquared implements Listener, ProtectionHook {
    private HashMap<Plot, HashSet<Location>> spawnersData = new HashMap<>();

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        return canInteract(player, location);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        return canInteract(player, location);
    }

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        return canInteract(player, location);
    }

    private static boolean canInteract(@NotNull final Player player, @NotNull Location location) {
        Plot plot = Plot.getPlot(com.plotsquared.core.location.Location.at(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()));
        if (plot == null) return true;
        return plot.isAdded(player.getUniqueId());
    }

    @Subscribe
    public void onPlotDelete(PlotDeleteEvent event) {
        if(event.getEventResult() == Result.DENY) return;
        Plot plot = event.getPlot();
        if(!spawnersData.containsKey(plot)) return;
        for(Location loc : spawnersData.get(plot)) {
            if(loc == null) return;
            SpawnerData spawner = SmartSpawner.getInstance().getSpawnerManager().getSpawnerByLocation(loc);
            if (spawner == null) continue;
            SmartSpawner.getInstance().getSpawnerManager().removeGhostSpawner(spawner.getSpawnerId());
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    private void onSpawnerPlace(SpawnerPlaceEvent e) {
        Location location = e.getLocation();
        if(location == null) return;
        Plot plot = Plot.getPlot(com.plotsquared.core.location.Location.at(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()));
        if(plot == null) return;
        if(!spawnersData.containsKey(plot)) spawnersData.put(plot, new HashSet<>());
        spawnersData.get(plot).add(location);
    }
}
