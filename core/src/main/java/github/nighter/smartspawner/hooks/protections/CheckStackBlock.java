package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.api.*;
import github.nighter.smartspawner.hooks.IntegrationManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckStackBlock {
    public static boolean CanPlayerPlaceBlock(@NotNull final Player player, @NotNull Location location) {
        if (player.isOp() || player.hasPermission("*")) return true;

        IntegrationManager integrationManager = SmartSpawner.getInstance().getIntegrationManager();

        if (integrationManager.isHasGriefPrevention() && !GriefPrevention.canPlayerStackClaimBlock(player, location))
            return false;
        if (integrationManager.isHasWorldGuard() && !WorldGuard.canPlayerStackBlockInRegion(player, location)) return false;
        if (integrationManager.isHasLands() && !Lands.canPlayerStackClaimBlock(player, location)) return false;
        if (integrationManager.isHasTowny() && !Towny.canPlayerInteractSpawner(player, location)) return false;
        if (integrationManager.isHasSuperiorSkyblock2() && SuperiorSkyblock2.canPlayerStackBlock(player, location)) return false;
        if (integrationManager.isHasBentoBox() && !BentoBoxAPI.canPlayerStackBlock(player, location)) return false;
        if (integrationManager.isHasSimpleClaimSystem() && !SimpleClaimSystem.canPlayerStackClaimBlock(player, location))
            return false;
        if (integrationManager.isHasSimpleClaimSystem2() && !SimpleClaimSystem2.canPlayerStackClaimBlock(player, location))
            return false;
        if (integrationManager.isHasMinePlots() && !MinePlots.canPlayerStackBlock(player, location)) return false;
        if (integrationManager.isHasIridiumSkyblock() && !IridiumSkyblock.canPlayerStackBlock(player, location)) return false;
        if (integrationManager.isHasPlotSquared() && !PlotSquared.canInteract(player, location)) return false;
        if (integrationManager.isHasResidence() && !Residence.canStack(player, location)) return false;
        return true;
    }
}
