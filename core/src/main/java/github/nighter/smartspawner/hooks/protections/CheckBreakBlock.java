package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.api.*;
import github.nighter.smartspawner.hooks.IntegrationManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckBreakBlock {
    public static boolean CanPlayerBreakBlock(@NotNull final Player player, @NotNull Location location) {
        if(player.isOp() || player.hasPermission("*")) return true;

        IntegrationManager integrationManager = SmartSpawner.getInstance().getIntegrationManager();

        if (integrationManager.isHasGriefPrevention() && !GriefPrevention.canPlayerBreakClaimBlock(player, location)) return false;
        if (integrationManager.isHasWorldGuard() && !WorldGuard.canPlayerBreakBlockInRegion(player, location)) return false;
        if (integrationManager.isHasLands() && !Lands.canPlayerBreakClaimBlock(player, location)) return false;
        if (integrationManager.isHasTowny() && !Towny.canPlayerInteractSpawner(player, location)) return false;
        if (integrationManager.isHasSimpleClaimSystem() && !SimpleClaimSystem.canPlayerBreakClaimBlock(player, location)) return false;
        if (integrationManager.isHasSimpleClaimSystem2() && !SimpleClaimSystem2.canPlayerBreakClaimBlock(player, location)) return false;
        if (integrationManager.isHasPlotSquared() && !PlotSquared.canInteract(player, location)) return false;
        if (integrationManager.isHasResidence() && !Residence.canPlayerBreakBlock(player, location)) return false;
        if (integrationManager.isHasMinePlots() && !MinePlots.canPlayerBreakBlock(player, location)) return false;
        return true;
    }
}
