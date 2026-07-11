package github.nighter.smartspawner.hooks.protections.api;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RedProtectAPI {

    public static boolean canPlayerOpenMenuOnClaim(Player player, Location location) {
        Region rg = RedProtect.get().getAPI().getRegion(location);
        return rg != null && rg.canChest(player); // Player can open menu
        // Player cannot open menu
    }

    public static boolean canPlayerStackClaimBlock(Player player, Location location) {
        Region rg = RedProtect.get().getAPI().getRegion(location);
        return rg != null && rg.canBuild(player); // Player can stack block
        // Player cannot stack block
    }
}
