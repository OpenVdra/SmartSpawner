package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.spawner.model.SpawnerData;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

class RangeMath {
    private final List<SpawnerData> spawners;
    private final PlayerRangeWrapper[] rangePlayers;

    public RangeMath(PlayerRangeWrapper[] players, List<SpawnerData> spawners) {
        this.spawners = spawners;
        this.rangePlayers = players;
    }

    public boolean[] getActiveSpawners() {
        final boolean[] activeSpawners = new boolean[spawners.size()];
        boolean playerFound;

        for (int i = 0; i < spawners.size(); i++) {
            SpawnerData s = spawners.get(i);
            final Location spawnerLoc = s.getSpawnerLocation();
            if (spawnerLoc == null) continue;

            final World locWorld = spawnerLoc.getWorld();
            if (locWorld == null) continue;

            final UUID worldUID = locWorld.getUID();
            final double rangeSq = s.getSpawnerRange() * s.getSpawnerRange();

            playerFound = false;

            for (PlayerRangeWrapper p : rangePlayers) {
                if (!p.spawnConditions()) continue;
                if (!worldUID.equals(p.worldUID())) continue;

                if (p.distanceSquared(spawnerLoc) <= rangeSq) {
                    playerFound = true;
                    break;
                }
            }

            activeSpawners[i] = playerFound;
        }

        return activeSpawners;
    }

}
