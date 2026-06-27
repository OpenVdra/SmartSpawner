package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

import java.util.Iterator;
import java.util.List;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;

    // Cached config values — refreshed via loadConfig() on reload
    private boolean protectSpawners;
    private boolean protectNatural;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        this.protectSpawners = plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true);
        this.protectNatural  = plugin.getConfig().getBoolean("natural_spawner.protect_from_explosions", false);
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event.blockList(), event.getExplosionResult());
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleExplosion(event.blockList(), event.getExplosionResult());
    }

    private void handleExplosion(List<Block> blockList, ExplosionResult explosionResult) {
        // Skip explosions that never destroy blocks:
        //   KEEP          — player-thrown WindCharge, BreezeWindCharge entity        
        //   TRIGGER_BLOCK — Mace Wind Burst enchantment (entity = struck mob, not player!)
        // All wind-related mechanics use ExplosionInteraction.TRIGGER in NMS → TRIGGER_BLOCK in Bukkit.
        if (explosionResult == ExplosionResult.KEEP || explosionResult == ExplosionResult.TRIGGER_BLOCK) {
            return;
        }

        // Only real destructive explosions reach here (TNT, Creeper, Wither, Respawn Anchor…)
        boolean hasApiListeners = SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0;

        Iterator<Block> it = blockList.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Material type = block.getType();

            if (type == Material.SPAWNER) {
                if (plugin.getSpawnerRemovalService().isRemovalPending(block.getLocation())) {
                    it.remove();
                    continue;
                }

                SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    if (protectSpawners) {
                        it.remove();
                        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawnerData);
                        cleanupAssociatedHopper(block);
                        if (hasApiListeners) {
                            Bukkit.getPluginManager().callEvent(new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, false, spawnerData.getEntityType()));
                        }
                    } else {
                        spawnerData.getSpawnerStop().set(true);
                        String spawnerId = spawnerData.getSpawnerId();
                        cleanupAssociatedHopper(block);
                        if (hasApiListeners) {
                            Bukkit.getPluginManager().callEvent(new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, true, spawnerData.getEntityType()));
                        }
                        spawnerManager.removeSpawner(spawnerId);
                        spawnerManager.markSpawnerDeleted(spawnerId);
                    }
                } else if (protectNatural) {
                    it.remove();
                }
            } else if (type == Material.RESPAWN_ANCHOR) {
                if (protectSpawners && hasProtectedSpawnersNearby(block)) {
                    it.remove();
                }
            }
        }
    }

    private boolean hasProtectedSpawnersNearby(Block anchorBlock) {
        if (!protectSpawners) return false;
        int protectionRadius = 8;

        for (int x = -protectionRadius; x <= protectionRadius; x++) {
            for (int y = -protectionRadius; y <= protectionRadius; y++) {
                for (int z = -protectionRadius; z <= protectionRadius; z++) {
                    Block nearbyBlock = anchorBlock.getRelative(x, y, z);
                    if (nearbyBlock.getType() == Material.SPAWNER) {
                        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(nearbyBlock.getLocation());
                        if (spawnerData != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void cleanupAssociatedHopper(Block block) {
        HopperService currentHopperService = plugin.getHopperService();
        if (currentHopperService == null) {
            return;
        }

        currentHopperService.getTracker().removeBelowSpawner(block);
    }
}
