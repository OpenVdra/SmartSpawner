package github.nighter.smartspawner.hooks.protections.api;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandChunkFlags;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;

import com.bgsoftware.superiorskyblock.api.world.Dimension;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class SuperiorSkyblock2 implements Listener {

    private static final String SPAWNER_STACK_PERM = "spawner_stack";
    private static final String SPAWNER_OPEN_MENU_PERM = "spawner_open_menu";
    private static IslandPrivilege SPAWNER_STACK, SPAWNER_OPEN_MENU;
    private static boolean registered = false;

    public SuperiorSkyblock2() {
        register();
    }

    public static void register() {
        if (registered)
            return;

        SPAWNER_STACK = getOrRegisterPrivilege(SPAWNER_STACK_PERM);
        SPAWNER_OPEN_MENU = getOrRegisterPrivilege(SPAWNER_OPEN_MENU_PERM);
        registered = true;
    }

    private static IslandPrivilege getOrRegisterPrivilege(String name) {
        try {
            return IslandPrivilege.getByName(name);
        } catch (NullPointerException ignored) {
            IslandPrivilege.register(name);
            return IslandPrivilege.getByName(name);
        }
    }

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), SPAWNER_STACK);
        }
        // Player is not in island
        return false;
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), SPAWNER_OPEN_MENU);
        }
        // Player is not in island
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIslandDisband(IslandDisbandEvent event) {
        if(event.isCancelled() || event.getIsland() == null) return;
        Island island = event.getIsland();
        for(Dimension dimension : Dimension.values()) {
            try {
                island.getAllChunksAsync(dimension, IslandChunkFlags.ONLY_PROTECTED | IslandChunkFlags.NO_EMPTY_CHUNKS, chunk -> {
                    for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.SPAWNER, false)) {
                        SpawnerData spawner = SmartSpawner.getInstance().getSpawnerManager().getSpawnerByLocation(state.getBlock().getLocation());
                        if (spawner == null) continue;
                        SmartSpawner.getInstance().getSpawnerManager().removeGhostSpawner(spawner.getSpawnerId());
                    }
                });
            } catch(NullPointerException ignored) {}
        }
    }
}
