package github.nighter.smartspawner.spawner.antiesp;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ChunkUtil;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Anti-ESP occlusion system optimized for Paper and Folia.
 * <p>
 * Raytraces line-of-sight from players to spawners. When a spawner is occluded
 * behind solid blocks, it disguises the block to prevent SpawnerESP / X-Ray hacks
 * and hides the hologram TextDisplay entity to defeat EntityESP / Tracers.
 */
public class AntiEspManager implements Listener {

    public enum RayTraceAccuracy {
        LOW(new Vector[]{
                new Vector(0.5, 0.5, 0.5)
        }),
        MEDIUM(new Vector[]{
                new Vector(0.5, 0.5, 0.5),
                new Vector(0.5, 0.85, 0.5),
                new Vector(0.5, 0.15, 0.5),
                new Vector(0.15, 0.5, 0.5),
                new Vector(0.85, 0.5, 0.5),
                new Vector(0.5, 0.5, 0.15),
                new Vector(0.5, 0.5, 0.85)
        }),
        HIGH(new Vector[]{
                new Vector(0.5, 0.5, 0.5),
                new Vector(0.15, 0.15, 0.15),
                new Vector(0.85, 0.15, 0.15),
                new Vector(0.15, 0.85, 0.15),
                new Vector(0.85, 0.85, 0.15),
                new Vector(0.15, 0.15, 0.85),
                new Vector(0.85, 0.15, 0.85),
                new Vector(0.15, 0.85, 0.85),
                new Vector(0.85, 0.85, 0.85)
        });

        private final Vector[] offsets;

        RayTraceAccuracy(Vector[] offsets) {
            this.offsets = offsets;
        }

        public Vector[] getOffsets() {
            return offsets;
        }

        public static RayTraceAccuracy fromString(String name) {
            if (name == null) return MEDIUM;
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }
    }

    private static final BlockData STONE_DATA = Material.STONE.createBlockData();
    private static final BlockData DEEPSLATE_DATA = Material.DEEPSLATE.createBlockData();
    private static final BlockData NETHERRACK_DATA = Material.NETHERRACK.createBlockData();
    private static final BlockData END_STONE_DATA = Material.END_STONE.createBlockData();

    private final SmartSpawner plugin;
    private final ExecutorService executor;

    // Config options
    private boolean enabled;
    private int checkInterval;
    private double maxDistance;
    private double maxDistanceSq;
    private double proximityBypassDistance;
    private double proximityBypassDistanceSq;
    private boolean disguiseBlock;
    private boolean hideHologram;
    private String disguiseMode;
    private Material customMaterial;
    private BlockData customBlockData;
    private RayTraceAccuracy accuracy;

    // Runtime state
    private Scheduler.Task tickTask;
    private final Map<Long, Set<SpawnerData>> chunkSpawners = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> playerVisibility = new ConcurrentHashMap<>();
    private final Map<UUID, Vector> lastCheckedLocations = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong(0);

    public AntiEspManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SmartSpawner-AntiESP");
            t.setDaemon(true);
            return t;
        });
        loadConfig();
        startTask();
    }

    public synchronized void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("antiesp.enabled", true);
        this.checkInterval = Math.max(1, config.getInt("antiesp.check_interval", 4));
        this.maxDistance = Math.max(8.0, config.getDouble("antiesp.max_distance", 48.0));
        this.maxDistanceSq = this.maxDistance * this.maxDistance;

        this.proximityBypassDistance = Math.max(0.0, config.getDouble("antiesp.proximity_bypass_distance", 3.5));
        this.proximityBypassDistanceSq = this.proximityBypassDistance * this.proximityBypassDistance;

        this.disguiseBlock = config.getBoolean("antiesp.disguise_block", true);
        this.hideHologram = config.getBoolean("antiesp.hide_hologram", true);
        this.disguiseMode = config.getString("antiesp.disguise_mode", "AUTO");

        String matName = config.getString("antiesp.custom_material", "STONE");
        Material parsedMat = Material.matchMaterial(matName != null ? matName : "STONE");
        this.customMaterial = (parsedMat != null && parsedMat.isBlock()) ? parsedMat : Material.STONE;
        this.customBlockData = this.customMaterial.createBlockData();

        this.accuracy = RayTraceAccuracy.fromString(config.getString("antiesp.raytrace_accuracy", "MEDIUM"));
    }

    private synchronized void startTask() {
        stopTask();
        if (!enabled) return;
        this.tickTask = Scheduler.runTaskTimer(this::tick, checkInterval, checkInterval);
    }

    private synchronized void stopTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void reload() {
        boolean wasEnabled = this.enabled;
        loadConfig();
        syncSpawners();

        if (wasEnabled && !this.enabled) {
            revealAllSpawners();
            stopTask();
        } else if (this.enabled) {
            startTask();
        }
    }

    public void syncSpawners() {
        chunkSpawners.clear();
        SpawnerManager spawnerManager = plugin.getSpawnerManager();
        if (spawnerManager == null) return;

        for (SpawnerData spawner : spawnerManager.getAllSpawners()) {
            registerSpawner(spawner);
        }
    }

    public void registerSpawner(SpawnerData spawner) {
        Location loc = spawner.getSpawnerLocation();
        if (loc == null || loc.getWorld() == null) return;

        long key = ChunkUtil.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        chunkSpawners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(spawner);
    }

    public void unregisterSpawner(SpawnerData spawner) {
        String spawnerId = spawner.getSpawnerId();
        Location loc = spawner.getSpawnerLocation();
        if (loc == null || loc.getWorld() == null) return;

        long key = ChunkUtil.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Set<SpawnerData> set = chunkSpawners.get(key);
        if (set != null) {
            set.remove(spawner);
            if (set.isEmpty()) {
                chunkSpawners.remove(key);
            }
        }

        // Restore real block for any player who currently has this spawner disguised
        for (Map.Entry<UUID, Map<String, Boolean>> entry : playerVisibility.entrySet()) {
            UUID uuid = entry.getKey();
            Boolean wasVisible = entry.getValue().remove(spawnerId);
            if (wasVisible != null && !wasVisible) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && player.getWorld().equals(loc.getWorld())) {
                    Scheduler.runEntityTask(player, () -> {
                        player.sendBlockChange(loc, loc.getBlock().getBlockData());
                    });
                }
            }
        }
    }

    private void tick() {
        if (!enabled) return;

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return;

        long currentTick = tickCounter.incrementAndGet();
        boolean forceFullRefresh = (currentTick % 20L == 0);

        List<PlayerSnapshot> snapshots = new ArrayList<>(players.size());
        for (Player p : players) {
            if (!p.isValid() || !p.isOnline()) continue;
            boolean bypass = p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("smartspawner.antiesp.bypass");
            snapshots.add(new PlayerSnapshot(
                    p,
                    p.getUniqueId(),
                    p.getWorld().getName(),
                    p.getEyeLocation(),
                    p.getLocation().getBlockX() >> 4,
                    p.getLocation().getBlockZ() >> 4,
                    bypass
            ));
        }

        executor.execute(() -> processSnapshots(snapshots, forceFullRefresh));
    }

    private record PlayerSnapshot(
            Player player,
            UUID uuid,
            String worldName,
            Location eyeLocation,
            int chunkX,
            int chunkZ,
            boolean bypass
    ) {}

    private void processSnapshots(List<PlayerSnapshot> snapshots, boolean forceRefresh) {
        int chunkRadius = (int) Math.ceil(maxDistance / 16.0);
        Vector[] offsets = accuracy.getOffsets();

        for (PlayerSnapshot s : snapshots) {
            Player player = s.player();
            UUID uuid = s.uuid();
            if (!player.isOnline()) continue;

            Map<String, Boolean> spawnerVisibilityMap = playerVisibility.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            Set<String> inRangeSpawnerIds = new HashSet<>();

            Vector currentPos = s.eyeLocation().toVector();
            Vector lastPos = lastCheckedLocations.get(uuid);
            boolean moved = (lastPos == null || lastPos.distanceSquared(currentPos) > 0.04);
            if (moved) {
                lastCheckedLocations.put(uuid, currentPos);
            }

            // If player has staff bypass, reveal every spawner in render distance
            if (s.bypass()) {
                for (int cx = s.chunkX() - chunkRadius; cx <= s.chunkX() + chunkRadius; cx++) {
                    for (int cz = s.chunkZ() - chunkRadius; cz <= s.chunkZ() + chunkRadius; cz++) {
                        Set<SpawnerData> spawners = chunkSpawners.get(ChunkUtil.getChunkKey(cx, cz));
                        if (spawners == null) continue;
                        for (SpawnerData sd : spawners) {
                            if (!sd.getSpawnerLocation().getWorld().getName().equals(s.worldName())) continue;
                            inRangeSpawnerIds.add(sd.getSpawnerId());
                            applyVisibility(player, sd, true);
                        }
                    }
                }
                cleanupOutOfRangeSpawners(player, spawnerVisibilityMap, inRangeSpawnerIds);
                continue;
            }

            // Normal player check
            for (int cx = s.chunkX() - chunkRadius; cx <= s.chunkX() + chunkRadius; cx++) {
                for (int cz = s.chunkZ() - chunkRadius; cz <= s.chunkZ() + chunkRadius; cz++) {
                    Set<SpawnerData> spawners = chunkSpawners.get(ChunkUtil.getChunkKey(cx, cz));
                    if (spawners == null || spawners.isEmpty()) continue;

                    for (SpawnerData sd : spawners) {
                        Location spawnerLoc = sd.getSpawnerLocation();
                        if (spawnerLoc.getWorld() == null || !spawnerLoc.getWorld().getName().equals(s.worldName())) continue;

                        inRangeSpawnerIds.add(sd.getSpawnerId());

                        double dx = s.eyeLocation().getX() - (spawnerLoc.getX() + 0.5);
                        double dy = s.eyeLocation().getY() - (spawnerLoc.getY() + 0.5);
                        double dz = s.eyeLocation().getZ() - (spawnerLoc.getZ() + 0.5);
                        double distSq = dx * dx + dy * dy + dz * dz;

                        // Too far
                        if (distSq > maxDistanceSq) {
                            applyVisibility(player, sd, false);
                            continue;
                        }

                        // Proximity bypass: close enough to always be visible without raytracing
                        if (proximityBypassDistanceSq > 0 && distSq <= proximityBypassDistanceSq) {
                            applyVisibility(player, sd, true);
                            continue;
                        }

                        // Optimization: if stationary and not periodic refresh, retain previous visibility
                        if (!moved && !forceRefresh && spawnerVisibilityMap.containsKey(sd.getSpawnerId())) {
                            continue;
                        }

                        // Dispatch raytrace on spawner's region thread
                        Location eyeLocCopy = s.eyeLocation().clone();
                        Scheduler.runLocationTask(spawnerLoc, () -> {
                            if (!player.isOnline()) return;
                            boolean visible = checkLineOfSight(eyeLocCopy, spawnerLoc, offsets);
                            applyVisibility(player, sd, visible);
                        });
                    }
                }
            }

            cleanupOutOfRangeSpawners(player, spawnerVisibilityMap, inRangeSpawnerIds);
        }
    }

    private void cleanupOutOfRangeSpawners(Player player, Map<String, Boolean> spawnerVisibilityMap, Set<String> inRangeSpawnerIds) {
        Iterator<Map.Entry<String, Boolean>> it = spawnerVisibilityMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Boolean> entry = it.next();
            String id = entry.getKey();
            if (!inRangeSpawnerIds.contains(id)) {
                it.remove();
            }
        }
    }

    private boolean checkLineOfSight(Location eyeLoc, Location spawnerLoc, Vector[] offsets) {
        World world = spawnerLoc.getWorld();
        if (world == null || !world.equals(eyeLoc.getWorld())) {
            return false;
        }

        // Folia safety check: do not cross unowned region threads
        if (Scheduler.isFolia()) {
            try {
                if (!Bukkit.isOwnedByCurrentRegion(spawnerLoc) || !Bukkit.isOwnedByCurrentRegion(eyeLoc)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        Vector eyeVec = eyeLoc.toVector();
        double spawnerBaseX = spawnerLoc.getBlockX();
        double spawnerBaseY = spawnerLoc.getBlockY();
        double spawnerBaseZ = spawnerLoc.getBlockZ();

        for (Vector offset : offsets) {
            Vector target = new Vector(spawnerBaseX + offset.getX(), spawnerBaseY + offset.getY(), spawnerBaseZ + offset.getZ());
            Vector dir = target.clone().subtract(eyeVec);
            double dist = dir.length();
            if (dist <= 0.2) {
                return true;
            }

            dir.normalize();
            double checkDist = Math.max(0.1, dist - 0.15);

            try {
                RayTraceResult hit = world.rayTraceBlocks(
                        eyeLoc,
                        dir,
                        checkDist,
                        FluidCollisionMode.NEVER,
                        true,
                        block -> block.getType() != Material.AIR && block.getBlockData().isOccluding()
                );

                // If nothing occluding was hit, ray reached the spawner
                if (hit == null) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    private void applyVisibility(Player player, SpawnerData spawner, boolean visible) {
        UUID uuid = player.getUniqueId();
        String spawnerId = spawner.getSpawnerId();
        Location loc = spawner.getSpawnerLocation();

        Map<String, Boolean> map = playerVisibility.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Boolean previous = map.put(spawnerId, visible);

        // Skip sending packets if visibility state didn't change
        if (previous != null && previous == visible) {
            return;
        }

        Scheduler.runEntityTask(player, () -> {
            if (!player.isOnline()) return;

            if (disguiseBlock) {
                if (visible) {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                    if (loc.getBlock().getState(false) instanceof TileState tileState) {
                        try {
                            player.sendBlockUpdate(loc, tileState);
                        } catch (Exception ignored) {}
                    }
                } else {
                    BlockData fakeData = getDisguisedBlockData(loc);
                    player.sendBlockChange(loc, fakeData);
                }
            }

            if (hideHologram && spawner.getHologram() != null) {
                TextDisplay display = spawner.getHologram().getTextDisplay();
                if (display != null && display.isValid()) {
                    if (visible) {
                        if (!player.canSee(display)) {
                            player.showEntity(plugin, display);
                        }
                    } else {
                        if (player.canSee(display)) {
                            player.hideEntity(plugin, display);
                        }
                    }
                }
            }
        });
    }

    public BlockData getDisguisedBlockData(Location loc) {
        if (!"AUTO".equalsIgnoreCase(disguiseMode)) {
            return customBlockData;
        }
        World world = loc.getWorld();
        if (world == null) return STONE_DATA;

        World.Environment env = world.getEnvironment();
        return switch (env) {
            case NETHER -> NETHERRACK_DATA;
            case THE_END -> END_STONE_DATA;
            default -> loc.getBlockY() < 0 ? DEEPSLATE_DATA : STONE_DATA;
        };
    }

    public void revealSpawner(Player player, SpawnerData spawner) {
        applyVisibility(player, spawner, true);
    }

    public void revealAllSpawners() {
        for (Map.Entry<UUID, Map<String, Boolean>> entry : playerVisibility.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            for (Map.Entry<String, Boolean> spawnerEntry : entry.getValue().entrySet()) {
                SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerEntry.getKey());
                if (spawner != null) {
                    Location loc = spawner.getSpawnerLocation();
                    Scheduler.runEntityTask(player, () -> {
                        player.sendBlockChange(loc, loc.getBlock().getBlockData());
                        if (spawner.getHologram() != null) {
                            TextDisplay td = spawner.getHologram().getTextDisplay();
                            if (td != null && td.isValid() && !player.canSee(td)) {
                                player.showEntity(plugin, td);
                            }
                        }
                    });
                }
            }
        }
        playerVisibility.clear();
    }

    public void cleanup() {
        stopTask();
        revealAllSpawners();
        chunkSpawners.clear();
        playerVisibility.clear();
        lastCheckedLocations.clear();
        executor.shutdown();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();
        if (loc.getWorld() == null) return;

        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerByLocation(loc);
        if (spawner != null) {
            revealSpawner(event.getPlayer(), spawner);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerByLocation(loc);
        if (spawner != null) {
            revealSpawner(event.getPlayer(), spawner);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerVisibility.remove(uuid);
        lastCheckedLocations.remove(uuid);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerVisibility.remove(uuid);
        lastCheckedLocations.remove(uuid);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) > 64.0) {
            lastCheckedLocations.remove(event.getPlayer().getUniqueId());
        }
    }
}
