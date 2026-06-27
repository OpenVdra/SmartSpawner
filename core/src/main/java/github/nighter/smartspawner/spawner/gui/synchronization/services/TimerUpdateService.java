package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager;
import github.nighter.smartspawner.spawner.gui.synchronization.utils.LootPreGenerationHelper;
import github.nighter.smartspawner.spawner.gui.synchronization.utils.TimerFormatter;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service responsible for managing and updating spawner timers in GUIs.
 * Handles timer calculation, display formatting, and periodic updates for main menu viewers.
 */
public class TimerUpdateService {

    private static final int MAX_PLAYERS_PER_BATCH = 10; // Limit players processed per batch

    // Pre-compiled regex patterns for better performance
    private static final Pattern TIMER_PATTERN = Pattern.compile("\\d{2}:\\d{2}");

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final LootPreGenerationHelper lootHelper;
    private final ViewerTrackingManager viewerTrackingManager;

    // Cached status text messages for timer display
    private String cachedInactiveText;
    private String cachedFullText;
    private String cachedNoLootText;

    // Timer placeholder detection
    private volatile Boolean hasTimerPlaceholders = null;

    // Performance tracking
    private final Map<UUID, Long> lastTimerUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTimerValue = new ConcurrentHashMap<>();

    // Cache the lore line index where timer is located per spawner
    private final Map<String, Integer> timerLineIndexCache = new ConcurrentHashMap<>();

    public TimerUpdateService(SmartSpawner plugin, ViewerTrackingManager viewerTrackingManager) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.lootHelper = new LootPreGenerationHelper(plugin);
        this.viewerTrackingManager = viewerTrackingManager;
        initializeCachedStrings();
    }

    /**
     * Initializes cached strings and detects timer placeholder usage.
     */
    private void initializeCachedStrings() {
        cachedInactiveText = languageManager.getGuiItemName("spawner_info_item.lore_inactive");
        cachedFullText = languageManager.getGuiItemName("spawner_info_item.lore_full");
        cachedNoLootText = languageManager.getGuiItemName("spawner_info_item.lore_no_loot");
        checkTimerPlaceholderUsage();
    }

    /**
     * Checks if the GUI configuration uses {time} placeholders.
     */
    private void checkTimerPlaceholderUsage() {
        try {
            String[] loreLines = languageManager.getGuiItemLore("spawner_info_item.lore");
            String[] loreNoShopLines = languageManager.getGuiItemLore("spawner_info_item.lore_no_shop");

            boolean hasTimers = false;

            if (loreLines != null) {
                for (String line : loreLines) {
                    if (line != null && line.contains("{time}")) {
                        hasTimers = true;
                        break;
                    }
                }
            }

            if (!hasTimers && loreNoShopLines != null) {
                for (String line : loreNoShopLines) {
                    if (line != null && line.contains("{time}")) {
                        hasTimers = true;
                        break;
                    }
                }
            }

            hasTimerPlaceholders = hasTimers;
        } catch (Exception e) {
            hasTimerPlaceholders = true; // Fallback to enabled if we can't determine
        }
    }

    /**
     * Checks if timer placeholders are enabled.
     *
     * @return true if timer placeholders are used in GUI
     */
    public boolean isTimerPlaceholdersEnabled() {
        return hasTimerPlaceholders == null || hasTimerPlaceholders;
    }

    /**
     * Checks if timer updates should be processed.
     * More efficient than isTimerPlaceholdersEnabled() as it returns false immediately
     * when we're certain timers are disabled.
     *
     * @return true if timer updates should be processed
     */
    public boolean shouldProcessTimerUpdates() {
        return hasTimerPlaceholders == null || hasTimerPlaceholders;
    }

    /**
     * Re-checks timer placeholder usage after configuration reload.
     * Clears all caches to ensure fresh state.
     */
    public void recheckTimerPlaceholders() {
        // Clear all caches before rechecking
        timerLineIndexCache.clear();
        lastTimerUpdate.clear();
        lastTimerValue.clear();

        // Reinitialize and recheck
        initializeCachedStrings();
    }

    /**
     * Calculates and returns the timer display string for a spawner.
     *
     * @param spawner The spawner to calculate timer for
     * @param player The player viewing (can be null)
     * @return Formatted timer string
     */
    public String calculateTimerDisplay(SpawnerData spawner, Player player) {
        if (!isTimerPlaceholdersEnabled()) {
            return "";
        }

        if (player != null && player.getGameMode() == GameMode.SPECTATOR) {
            return cachedInactiveText;
        }

        // Check if spawner has no configured loot or experience
        if (spawner.hasNoLootOrExperience()) {
            return cachedNoLootText;
        }

        if (spawner.getIsAtCapacity()) {
            return cachedFullText;
        }

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        
        if (timeUntilNextSpawn == -1) {
            return cachedInactiveText;
        }
        
        return TimerFormatter.formatTime(timeUntilNextSpawn);
    }

    /**
     * Processes timer updates for all main menu viewers.
     * This is called periodically by the update task.
     * Optimized to minimize repeated Player object lookups and inventory checks.
     */
    public void processTimerUpdates() {
        // Early exit if no timer placeholders in GUI config
        if (hasTimerPlaceholders != null && !hasTimerPlaceholders) {
            return;
        }

        if (!viewerTrackingManager.hasMainMenuViewers()) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Group main menu viewers by spawner
        Map<String, List<PlayerViewerContext>> spawnerViewers = new HashMap<>();
        Map<UUID, ViewerTrackingManager.ViewerInfo> mainMenuViewers = viewerTrackingManager.getMainMenuViewers();

        for (Map.Entry<UUID, ViewerTrackingManager.ViewerInfo> entry : mainMenuViewers.entrySet()) {
            UUID playerId = entry.getKey();
            ViewerTrackingManager.ViewerInfo viewerInfo = entry.getValue();
            SpawnerData spawner = viewerInfo.getSpawnerData();

            // Skip if updated recently (throttle to 800ms) - check before expensive operations
            Long lastUpdate = lastTimerUpdate.get(playerId);
            if (lastUpdate != null && (currentTime - lastUpdate) < 800) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                viewerTrackingManager.untrackViewer(playerId);
                continue;
            }

            // Get inventory once and validate
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (!(openInventory.getHolder(false) instanceof SpawnerMenuHolder)) {
                viewerTrackingManager.untrackViewer(playerId);
                continue;
            }

            // Store player and inventory context to avoid repeated lookups
            spawnerViewers.computeIfAbsent(spawner.getSpawnerId(), k -> new ArrayList<>())
                .add(new PlayerViewerContext(playerId, player, openInventory, player.getLocation()));
        }

        int processedPlayers = 0;

        // Process spawners in batches
        for (Map.Entry<String, List<PlayerViewerContext>> spawnerGroup : spawnerViewers.entrySet()) {
            List<PlayerViewerContext> viewers = spawnerGroup.getValue();
            if (viewers.isEmpty()) continue;

            // Get spawner from first viewer - cache the lookup
            PlayerViewerContext firstViewer = viewers.get(0);
            ViewerTrackingManager.ViewerInfo viewerInfo = mainMenuViewers.get(firstViewer.playerId);
            if (viewerInfo == null) continue;

            SpawnerData spawner = viewerInfo.getSpawnerData();

            // Calculate timer once for this spawner
            String newTimerValue = calculateTimerDisplayInternal(spawner);

            // Apply to all viewers
            for (PlayerViewerContext context : viewers) {
                if (processedPlayers >= MAX_PLAYERS_PER_BATCH) {
                    break;
                }

                // Skip if timer unchanged
                String lastValue = lastTimerValue.get(context.playerId);
                if (lastValue != null && lastValue.equals(newTimerValue)) {
                    continue;
                }

                // Update tracking
                lastTimerUpdate.put(context.playerId, currentTime);
                lastTimerValue.put(context.playerId, newTimerValue);
                processedPlayers++;

                // Schedule update on player's thread using cached location
                if (context.location != null) {
                    final String finalTimerValue = newTimerValue;
                    final UUID finalPlayerId = context.playerId;

                    Scheduler.runLocationTask(context.location, () -> {
                        // Quick validation - player and inventory already validated above
                        if (!context.player.isOnline() || !mainMenuViewers.containsKey(finalPlayerId)) {
                            return;
                        }

                        // Revalidate inventory in case it changed
                        Inventory currentInv = context.player.getOpenInventory().getTopInventory();
                        if (currentInv == null || !(currentInv.getHolder(false) instanceof SpawnerMenuHolder)) {
                            viewerTrackingManager.untrackViewer(finalPlayerId);
                            return;
                        }

                        int spawnerInfoSlot = getSpawnerInfoSlot(currentInv);
                        if (spawnerInfoSlot >= 0) {
                            updateSpawnerInfoItemTimer(currentInv, spawner, finalTimerValue, spawnerInfoSlot);
                            context.player.updateInventory();
                        }
                    });
                }
            }

            if (processedPlayers >= MAX_PLAYERS_PER_BATCH) {
                break;
            }
        }
    }

    /**
     * Forces immediate timer update for spawner state changes.
     *
     * @param spawner The spawner whose state changed
     */
    public void forceStateChangeUpdate(SpawnerData spawner) {
        if (!isTimerPlaceholdersEnabled()) {
            return;
        }

        Set<UUID> mainMenuViewerSet = viewerTrackingManager.getMainMenuViewersForSpawner(spawner.getSpawnerId());
        if (mainMenuViewerSet == null || mainMenuViewerSet.isEmpty()) {
            return;
        }

        // Clear previous values to force refresh
        for (UUID viewerId : mainMenuViewerSet) {
            lastTimerUpdate.remove(viewerId);
            lastTimerValue.remove(viewerId);
        }

        updateMainMenuViewers(spawner);
    }

    /**
     * Forces immediate timer update for inactive spawners.
     *
     * @param player The player
     * @param spawner The spawner
     */
    public void forceTimerUpdateInactive(Player player, SpawnerData spawner) {
        spawner.clearPreGeneratedLoot();
        if (!isTimerPlaceholdersEnabled()) {
            return;
        }

        if (!isValidGuiSession(player)) {
            return;
        }

        Location playerLocation = player.getLocation();
        if (playerLocation == null) {
            return;
        }

        Scheduler.runLocationTask(playerLocation, () -> {
            if (!player.isOnline()) {
                return;
            }

            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory == null || !(openInventory.getHolder(false) instanceof SpawnerMenuHolder)) {
                return;
            }

            int spawnerInfoSlot = getSpawnerInfoSlot(openInventory);
            if (spawnerInfoSlot >= 0) {
                String timerValue = cachedInactiveText;
                updateSpawnerInfoItemTimer(openInventory, spawner, timerValue, spawnerInfoSlot);
                player.updateInventory();
            }
        });
    }

    /**
     * Internal method to calculate timer display with loot pre-generation.
     */
    private String calculateTimerDisplayInternal(SpawnerData spawner) {
        // Check if spawner has no configured loot or experience
        if (spawner.hasNoLootOrExperience()) {
            return cachedNoLootText;
        }

        if (spawner.getIsAtCapacity()) {
            return cachedFullText;
        }

        if (spawner.getSpawnerStop().get()) {
            spawner.clearPreGeneratedLoot();
            return cachedInactiveText;
        }

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        return TimerFormatter.formatTime(timeUntilNextSpawn);
    }

    /**
     * Updates main menu viewers immediately.
     */
    private void updateMainMenuViewers(SpawnerData spawner) {
        Set<UUID> mainMenuViewerSet = viewerTrackingManager.getMainMenuViewersForSpawner(spawner.getSpawnerId());
        if (mainMenuViewerSet == null || mainMenuViewerSet.isEmpty()) {
            return;
        }

        String timerValue = calculateTimerDisplayInternal(spawner);

        for (UUID viewerId : new HashSet<>(mainMenuViewerSet)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (!isValidGuiSession(viewer)) {
                viewerTrackingManager.untrackViewer(viewerId);
                continue;
            }

            Location loc = viewer.getLocation();
            if (loc != null) {
                final String finalTimerValue = timerValue;
                final UUID finalViewerId = viewerId;

                Scheduler.runLocationTask(loc, () -> {
                    if (!viewer.isOnline() || !viewerTrackingManager.getMainMenuViewers().containsKey(finalViewerId)) {
                        return;
                    }

                    Inventory openInv = viewer.getOpenInventory().getTopInventory();
                    if (openInv == null || !(openInv.getHolder(false) instanceof SpawnerMenuHolder)) {
                        viewerTrackingManager.untrackViewer(finalViewerId);
                        return;
                    }

                    updateSpawnerInfoItemTimer(
                            openInv, spawner, finalTimerValue, getSpawnerInfoSlot(openInv));
                    viewer.updateInventory();
                });
            }
        }
    }

    private int getSpawnerInfoSlot(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof SpawnerMenuHolder holder)
                || holder.getInfoButton() == null) {
            return -1;
        }
        return holder.getInfoButton().getSlot();
    }

    /**
     * Calculates time until next spawn for GUI display purposes only.
     * Actual loot spawning is handled by SpawnerRangeChecker independently.
     */
    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        long cachedDelay = spawner.getCachedSpawnDelay();
        if (cachedDelay == 0) {
            cachedDelay = (spawner.getSpawnDelay() + 20L) * 50L; // Convert ticks to milliseconds
            spawner.setCachedSpawnDelay(cachedDelay);
        }

        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long timeElapsed = currentTime - lastSpawnTime;

        long timeUntilNextSpawn = cachedDelay - timeElapsed;
        timeUntilNextSpawn = Math.max(0, Math.min(timeUntilNextSpawn, cachedDelay));
        
        // Pre-generate loot when timer is low for smooth GUI display
        if (lootHelper.shouldPreGenerateLoot(timeUntilNextSpawn)) {
            lootHelper.preGenerateLoot(spawner);
        }

        // Add pre-generated loot early for instant spawn at 00:00
        if (lootHelper.shouldAddLootEarly(timeUntilNextSpawn)) {
            lootHelper.addPreGeneratedLootEarly(spawner, cachedDelay);
        }

        return timeUntilNextSpawn;
    }

    /**
     * Updates timer in spawner info item.
     * Optimized to minimize hasItemMeta/getItemMeta calls and object allocations.
     */
    private void updateSpawnerInfoItemTimer(Inventory inventory, SpawnerData spawner, 
                                           String timeDisplay, int spawnerInfoSlot) {
        if (spawnerInfoSlot < 0) {
            return;
        }

        ItemStack spawnerItem = inventory.getItem(spawnerInfoSlot);
        if (spawnerItem == null) {
            return;
        }

        // Get meta once - this is the expensive operation
        ItemMeta meta = spawnerItem.getItemMeta();
        if (meta == null) {
            return;
        }

        // Get lore once - this creates a defensive copy, avoid multiple calls
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return;
        }

        // Try to use cached index first
        String spawnerId = spawner.getSpawnerId();
        Integer cachedIndex = timerLineIndexCache.get(spawnerId);

        boolean needsUpdate = false;
        int updatedIndex = -1;
        String updatedLine = null;

        // Fast path: try cached index first
        if (cachedIndex != null && cachedIndex >= 0 && cachedIndex < lore.size()) {
            String line = lore.get(cachedIndex);
            updatedLine = tryUpdateTimerLine(line, timeDisplay);

            if (updatedLine != null && !updatedLine.equals(line)) {
                updatedIndex = cachedIndex;
                needsUpdate = true;
            } else if (updatedLine == null) {
                // Cache is stale, invalidate and fall through to search
                timerLineIndexCache.remove(spawnerId);
                cachedIndex = null;
            }
        }

        // Slow path: search for timer line if cache miss or invalid
        if (!needsUpdate && cachedIndex == null) {
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                updatedLine = tryUpdateTimerLine(line, timeDisplay);

                if (updatedLine != null && !updatedLine.equals(line)) {
                    updatedIndex = i;
                    needsUpdate = true;
                    // Cache this index for future updates
                    timerLineIndexCache.put(spawnerId, i);
                    break;
                }
            }
        }

        if (needsUpdate && updatedIndex >= 0) {
            lore.set(updatedIndex, updatedLine);
            meta.setLore(lore);
            spawnerItem.setItemMeta(meta);
            // Direct slot update without getting item again
            inventory.setItem(spawnerInfoSlot, spawnerItem);
        }
    }

    /**
     * Attempts to update a timer line. Returns updated line if this line contains timer info,
     * or null if this line doesn't contain timer information.
     */
    private String tryUpdateTimerLine(String line, String timeDisplay) {
        // Check for placeholder first (initial GUI creation)
        if (line.contains("{time}")) {
            return line.replace("{time}", timeDisplay);
        }

        // Quick reject: skip line if it can't possibly contain timer (no colon = no HH:MM pattern)
        if (line.indexOf(':') == -1) {
            return null;
        }

        // Try to update existing timer pattern
        String updatedLine = updateExistingTimerLine(line, timeDisplay);
        if (!updatedLine.equals(line)) {
            return updatedLine;
        }

        return null; // This line doesn't contain timer info
    }

    /**
     * Updates existing timer line by replacing old value with new.
     * Only handles timer pattern (HH:MM) replacement for language independence.
     */
    private String updateExistingTimerLine(String line, String newTimeDisplay) {
        // Quick check: if line doesn't contain ':', it can't have a timer
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            return line;
        }

        // Check if line contains a timer pattern (HH:MM format) using pre-compiled pattern
        if (TIMER_PATTERN.matcher(line).find()) {
            // Replace time pattern directly
            return TIMER_PATTERN.matcher(line).replaceFirst(newTimeDisplay);
        }

        return line;
    }

    private boolean isValidGuiSession(Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Clears performance tracking for a player.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerTracking(UUID playerId) {
        lastTimerUpdate.remove(playerId);
        lastTimerValue.remove(playerId);
    }

    /**
     * Clears all performance tracking.
     */
    public void clearAllTracking() {
        lastTimerUpdate.clear();
        lastTimerValue.clear();
    }

    /**
     * Context object to cache player-related lookups and avoid repeated method calls.
     * Reduces performance overhead from multiple Player.isOnline(), getOpenInventory(), etc.
     */
    private static class PlayerViewerContext {
        final UUID playerId;
        final Player player;
        final Inventory inventory;
        final Location location;

        PlayerViewerContext(UUID playerId, Player player, Inventory inventory, Location location) {
            this.playerId = playerId;
            this.player = player;
            this.inventory = inventory;
            this.location = location;
        }
    }
}
