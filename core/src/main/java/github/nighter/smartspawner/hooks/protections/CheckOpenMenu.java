package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.protections.api.*;
import github.nighter.smartspawner.hooks.IntegrationManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckOpenMenu {
    public static boolean CanPlayerOpenMenu(@NotNull final Player player, @NotNull Location location) {
        if (player.isOp() || player.hasPermission("*")) return true;

        IntegrationManager integrationManager = SmartSpawner.getInstance().getIntegrationManager();

        if (integrationManager.isHasGriefPrevention() && !GriefPrevention.canPlayerOpenMenuOnClaim(player, location))
            return false;
        if (integrationManager.isHasWorldGuard() && !WorldGuard.canPlayerInteractInRegion(player, location)) return false;
        if (integrationManager.isHasLands() && !Lands.CanPlayerInteractContainer(player, location)) return false;
        if (integrationManager.isHasTowny() && !Towny.canPlayerInteractSpawner(player, location)) return false;
        if (integrationManager.isHasSuperiorSkyblock2() && SuperiorSkyblock2.canPlayerOpenMenu(player, location)) return false;
        if (integrationManager.isHasBentoBox() && !BentoBoxAPI.canPlayerOpenMenu(player, location)) return false;
        if (integrationManager.isHasSimpleClaimSystem() && !SimpleClaimSystem.canPlayerOpenMenuOnClaim(player, location))
            return false;
        if (integrationManager.isHasSimpleClaimSystem2() && !SimpleClaimSystem2.canPlayerOpenMenuOnClaim(player, location))
            return false;
        if (integrationManager.isHasMinePlots() && !MinePlots.canPlayerOpenMenu(player, location)) return false;
        if (integrationManager.isHasIridiumSkyblock() && !IridiumSkyblock.canPlayerOpenMenu(player, location)) return false;
        if (integrationManager.isHasPlotSquared() && !PlotSquared.canInteract(player, location)) return false;
        if (integrationManager.isHasResidence() && !Residence.canInteract(player, location)) return false;
        return true;
    }
}
