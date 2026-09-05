package github.nighter.smartspawner.spawner.gui.synchronization.managers;

import github.nighter.smartspawner.spawner.model.SpawnerData;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tracking of players viewing spawner GUIs.
 * Maintains mappings between players and spawners, categorized by viewer type.
 * Thread-safe implementation for Folia compatibility.
 */
public class ViewerTrackingManager {

    /**
     * Enum to categorize different types of spawner GUI viewers.
     */
    public enum ViewerType {
        MAIN_MENU,    // SpawnerMenuHolder - needs timer updates
        STORAGE,      // StoragePageHolder - no timer updates needed
        FILTER        // FilterConfigHolder - no timer updates needed
    }

    /**
     * Holds information about a viewer's relationship with a spawner.
     */
    public static class ViewerInfo {
        private final SpawnerData spawnerData;
        private final long lastUpdateTime;
        private final ViewerType viewerType;

        public ViewerInfo(SpawnerData spawnerData, ViewerType viewerType) {
            this.spawnerData = spawnerData;
            this.lastUpdateTime = System.currentTimeMillis();
            this.viewerType = viewerType;
        }

        public SpawnerData getSpawnerData() {
            return spawnerData;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public ViewerType getViewerType() {
            return viewerType;
        }
    }

    // Track all viewers
    private final Map<UUID, ViewerInfo> playerToSpawnerMap = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> spawnerToPlayersMap = new ConcurrentHashMap<>();

    /**
     * -- GETTER --
     *  Gets all main menu viewers.
     *
     * @return Map of player UUIDs to their viewer info
     */
    // Track main menu viewers separately for timer updates
    @Getter
    private final Map<UUID, ViewerInfo> mainMenuViewers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> spawnerToMainMenuViewers = new ConcurrentHashMap<>();

    // Track filter GUI viewers to prevent duplication exploits
    private final Map<String, Set<UUID>> spawnerToFilterViewersMap = new ConcurrentHashMap<>();

    // Track storage GUI viewers per spawner, kept separate so the batched task can iterate only
    // storage viewers and so first/last-viewer transitions (which drive the freeze) can be detected.
    private final Map<String, Set<UUID>> spawnerToStorageViewersMap = new ConcurrentHashMap<>();

    /**
     * Registers a player as viewing a specific spawner GUI.
     *
     * @param playerId The player's UUID
     * @param spawner The spawner being viewed
     * @param viewerType The type of GUI being viewed
     */
    public void trackViewer(UUID playerId, SpawnerData spawner, ViewerType viewerType) {
        ViewerInfo info = new ViewerInfo(spawner, viewerType);

        // Track all viewers
        playerToSpawnerMap.put(playerId, info);
        spawnerToPlayersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                .add(playerId);

        // Separately track main menu viewers for timer updates
        if (viewerType == ViewerType.MAIN_MENU) {
            mainMenuViewers.put(playerId, info);
            spawnerToMainMenuViewers.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                    .add(playerId);
        }

        // Separately track filter GUI viewers to prevent duplication exploits
        if (viewerType == ViewerType.FILTER) {
            spawnerToFilterViewersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                    .add(playerId);
        }

        // Separately track storage GUI viewers for version-based refresh and freeze
        if (viewerType == ViewerType.STORAGE) {
            spawnerToStorageViewersMap.computeIfAbsent(spawner.getSpawnerId(), k -> ConcurrentHashMap.newKeySet())
                    .add(playerId);
        }
    }

    /**
     * Unregisters a player from all viewer tracking.
     *
     * @param playerId The player's UUID
     */
    public void untrackViewer(UUID playerId) {
        ViewerInfo info = playerToSpawnerMap.remove(playerId);
        if (info != null) {
            SpawnerData spawner = info.spawnerData;
            String spawnerId = spawner.getSpawnerId();

            // Remove from general tracking
            Set<UUID> viewers = spawnerToPlayersMap.get(spawnerId);
            if (viewers != null) {
                viewers.remove(playerId);
                if (viewers.isEmpty()) {
                    spawnerToPlayersMap.remove(spawnerId);
                }
            }

            // Remove from filter viewer tracking
            Set<UUID> filterViewers = spawnerToFilterViewersMap.get(spawnerId);
            if (filterViewers != null) {
                filterViewers.remove(playerId);
                if (filterViewers.isEmpty()) {
                    spawnerToFilterViewersMap.remove(spawnerId);
                }
            }

            // Remove from storage viewer tracking
            Set<UUID> storageViewers = spawnerToStorageViewersMap.get(spawnerId);
            if (storageViewers != null) {
                storageViewers.remove(playerId);
                if (storageViewers.isEmpty()) {
                    spawnerToStorageViewersMap.remove(spawnerId);
                }
            }
        }

        // Remove from main menu tracking
        ViewerInfo mainMenuInfo = mainMenuViewers.remove(playerId);
        if (mainMenuInfo != null) {
            SpawnerData spawner = mainMenuInfo.spawnerData;
            Set<UUID> mainMenuViewerSet = spawnerToMainMenuViewers.get(spawner.getSpawnerId());
            if (mainMenuViewerSet != null) {
                mainMenuViewerSet.remove(playerId);
                if (mainMenuViewerSet.isEmpty()) {
                    spawnerToMainMenuViewers.remove(spawner.getSpawnerId());
                }
            }
        }
    }

    /**
     * Gets all online players viewing a specific spawner.
     *
     * @param spawnerId The spawner ID
     * @return Set of online players viewing the spawner
     */
    public Set<Player> getViewers(String spawnerId) {
        Set<UUID> viewerIds = spawnerToPlayersMap.get(spawnerId);
        if (viewerIds == null || viewerIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Player> onlineViewers = new HashSet<>(viewerIds.size());
        for (UUID id : viewerIds) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                onlineViewers.add(player);
            }
        }
        return onlineViewers;
    }

    /**
     * Checks if a spawner has any viewers.
     *
     * @param spawner The spawner data
     * @return true if the spawner has viewers
     */
    public boolean hasViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToPlayersMap.get(spawner.getSpawnerId());
        return viewers != null && !viewers.isEmpty();
    }

    /**
     * Gets viewer information for a specific player.
     *
     * @param playerId The player's UUID
     * @return ViewerInfo or null if not tracked
     */
    public ViewerInfo getViewerInfo(UUID playerId) {
        return playerToSpawnerMap.get(playerId);
    }

    /**
     * Gets main menu viewers for a specific spawner.
     *
     * @param spawnerId The spawner ID
     * @return Set of player UUIDs viewing the main menu, or null if none
     */
    public Set<UUID> getMainMenuViewersForSpawner(String spawnerId) {
        return spawnerToMainMenuViewers.get(spawnerId);
    }

    /**
     * Gets all viewers regardless of type.
     *
     * @return Map of player UUIDs to their viewer info
     */
    public Map<UUID, ViewerInfo> getAllViewers() {
        return playerToSpawnerMap;
    }

    /**
     * Gets viewers for a specific spawner as UUIDs.
     *
     * @param spawnerId The spawner ID
     * @return Set of viewer UUIDs, or null if none
     */
    public Set<UUID> getViewerIds(String spawnerId) {
        return spawnerToPlayersMap.get(spawnerId);
    }

    /**
     * Gets filter viewers for a specific spawner.
     *
     * @param spawnerId The spawner ID
     * @return Set of player UUIDs viewing filter GUI, or null if none
     */
    public Set<UUID> getFilterViewersForSpawner(String spawnerId) {
        return spawnerToFilterViewersMap.get(spawnerId);
    }

    /**
     * Checks whether a spawner has any storage GUI viewers.
     *
     * @param spawner The spawner data
     * @return true if at least one player has this spawner's storage GUI open
     */
    public boolean hasStorageViewers(SpawnerData spawner) {
        Set<UUID> viewers = spawnerToStorageViewersMap.get(spawner.getSpawnerId());
        return viewers != null && !viewers.isEmpty();
    }

    /**
     * Whether a spawner's storage GUI is currently open for someone other than {@code playerId}.
     * Used by the single-viewer gate: at most one player may have a spawner's storage open at a
     * time, which is what makes native item interaction dupe-safe (only one Bukkit inventory exists
     * per spawner). The gate does not depend on whether {@code playerId} is tracked yet, so it is
     * correct regardless of listener ordering at open time.
     *
     * @param spawner  the spawner whose storage is being opened
     * @param playerId the UUID of the player attempting to open
     * @return true if any different player already has this spawner's storage open
     */
    public boolean isStorageViewedByOther(SpawnerData spawner, UUID playerId) {
        Set<UUID> viewers = spawnerToStorageViewersMap.get(spawner.getSpawnerId());
        if (viewers == null || viewers.isEmpty()) {
            return false;
        }
        for (UUID viewer : viewers) {
            if (!viewer.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Snapshot of the current storage GUI viewers as (player UUID, spawner) pairs, for the batched
     * version-based refresh.
     *
     * @return a fresh list, safe to iterate while viewers change
     */
    public List<Map.Entry<UUID, SpawnerData>> getStorageViewerEntries() {
        List<Map.Entry<UUID, SpawnerData>> out = new ArrayList<>();
        for (Map.Entry<UUID, ViewerInfo> entry : playerToSpawnerMap.entrySet()) {
            ViewerInfo info = entry.getValue();
            if (info.getViewerType() == ViewerType.STORAGE) {
                out.add(Map.entry(entry.getKey(), info.getSpawnerData()));
            }
        }
        return out;
    }

    /**
     * Checks if there are any viewers being tracked.
     *
     * @return true if any viewers are tracked
     */
    public boolean hasAnyViewers() {
        return !playerToSpawnerMap.isEmpty();
    }

    /**
     * Checks if there are any main menu viewers.
     *
     * @return true if any main menu viewers exist
     */
    public boolean hasMainMenuViewers() {
        return !mainMenuViewers.isEmpty();
    }

    /**
     * Clears all tracking data.
     */
    public void clearAll() {
        playerToSpawnerMap.clear();
        spawnerToPlayersMap.clear();
        mainMenuViewers.clear();
        spawnerToMainMenuViewers.clear();
        spawnerToFilterViewersMap.clear();
        spawnerToStorageViewersMap.clear();
    }
}
