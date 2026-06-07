package github.nighter.smartspawner.spawner.gui.stacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerRemoveEvent;
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.config.SpawnerSettingsConfig;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.utils.SpawnerLocationLockManager;
import github.nighter.smartspawner.spawner.utils.SpawnerTypeChecker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpawnerStackerHandler implements Listener {
    private static final String DROP_CHANCE_BYPASS_PERMISSION = "smartspawner.break.bypassdropchance";
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerMenuUI spawnerMenuUI;
    private final LanguageManager languageManager;
    private final SpawnerItemFactory spawnerItemFactory;
    private final SpawnerLocationLockManager locationLockManager;
    private final github.nighter.smartspawner.spawner.data.SpawnerManager spawnerManager;

    // Sound constants
    private static final Sound STACK_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound CLICK_SOUND = Sound.UI_BUTTON_CLICK;
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    // GUI slots & operation constants
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};
    private static final int REMOVE_ALL_SLOT = 22;
    private static final int ADD_ALL_SLOT = 4;

    // Player interaction tracking - using more efficient data structures
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>(16, 0.75f, 2);
    private final Map<UUID, Scheduler.Task> pendingUpdates = new ConcurrentHashMap<>(16, 0.75f, 2);
    private final Map<String, Set<UUID>> activeViewers = new ConcurrentHashMap<>(16, 0.75f, 2);
    private final Map<UUID, AtomicBoolean> updateLocks = new ConcurrentHashMap<>(16, 0.75f, 2);

    // Cache for entity type extraction - avoids repeated metadata checks
    private final Map<ItemStack, Optional<EntityType>> entityTypeCache = new WeakHashMap<>();

    // Click cooldown in milliseconds
    private static final long CLICK_COOLDOWN = 200L;

    // Batch update delay in ticks
    private static final long UPDATE_DELAY = 2L;

    public SpawnerStackerHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.locationLockManager = plugin.getSpawnerLocationLockManager();
        this.spawnerManager = plugin.getSpawnerManager();

        // Start cleanup task - increased interval for less overhead
        startCleanupTask();
    }

    private void startCleanupTask() {
        Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            lastClickTime.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
            updateLocks.entrySet().removeIf(entry -> !lastClickTime.containsKey(entry.getKey()));

            // Clear entity type cache occasionally to prevent memory leaks
            if (entityTypeCache.size() > 100) {
                entityTypeCache.clear();
            }
        }, 200L, 200L); // Increased from 100L to 200L
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder(false) instanceof SpawnerStackerHolder holder)) return;

        // Cancel the event to prevent item movement
        event.setCancelled(true);

        SpawnerData spawner = holder.getSpawnerData();
        if (!canUseStacker(player, spawner)) {
            player.closeInventory();
            messageService.sendMessage(player, "stacker_drop_blocked");
            return;
        }

        // Check for cooldown - fast path return
        UUID playerId = player.getUniqueId();
        Long lastClick = lastClickTime.get(playerId);
        if (lastClick != null && System.currentTimeMillis() - lastClick < CLICK_COOLDOWN) return;

        // Get the clicked item
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Handle navigation back to main menu if spawner is clicked
        if (clickedItem.getType() == Material.SPAWNER) {
            // Check if player is Bedrock and use appropriate menu
            if (isBedrockPlayer(player)) {
                if (plugin.getSpawnerMenuFormUI() != null) {
                    plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                } else {
                    // Fallback to standard GUI if FormUI not available
                    spawnerMenuUI.openSpawnerMenu(player, spawner, true);
                }
            } else {
                spawnerMenuUI.openSpawnerMenu(player, spawner, true);
            }
            player.playSound(player.getLocation(), CLICK_SOUND, SOUND_VOLUME, SOUND_PITCH);
            return;
        }

        // Process stack modification
        int slotIndex = event.getRawSlot();

        // Handle "all" buttons
        if (slotIndex == ADD_ALL_SLOT) {
            lastClickTime.put(playerId, System.currentTimeMillis());
            handleAddAll(player, spawner);
            String spawnerId = spawner.getSpawnerId();
            Set<UUID> viewers = activeViewers.get(spawnerId);
            if (viewers != null && !viewers.isEmpty()) {
                scheduleViewersUpdate(spawner);
            }
            return;
        }
        if (slotIndex == REMOVE_ALL_SLOT) {
            lastClickTime.put(playerId, System.currentTimeMillis());
            handleRemoveAll(player, spawner);
            String spawnerId = spawner.getSpawnerId();
            Set<UUID> viewers = activeViewers.get(spawnerId);
            if (viewers != null && !viewers.isEmpty()) {
                scheduleViewersUpdate(spawner);
            }
            return;
        }

        int changeAmount = determineChangeAmount(slotIndex);

        if (changeAmount != 0) {
            // Mark click time and attempt modification
            lastClickTime.put(playerId, System.currentTimeMillis());
            processStackModification(player, spawner, changeAmount);

            // Schedule a single batch update for all viewers
            String spawnerId = spawner.getSpawnerId();
            Set<UUID> viewers = activeViewers.get(spawnerId);
            if (viewers != null && !viewers.isEmpty()) {
                scheduleViewersUpdate(spawner);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent any dragging in the stacker GUI
        if (!(event.getInventory().getHolder(false) instanceof SpawnerStackerHolder holder)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder(false) instanceof SpawnerStackerHolder holder)) return;

        if (!canUseStacker(player, holder.getSpawnerData())) {
            event.setCancelled(true);
            messageService.sendMessage(player, "stacker_drop_blocked");
            return;
        }

        // Track player as viewer of this spawner
        UUID playerId = player.getUniqueId();
        String spawnerId = holder.getSpawnerData().getSpawnerId();

        // Use computeIfAbsent for atomic operations
        Set<UUID> viewers = activeViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet());
        viewers.add(playerId);

        // Initialize lock for this player
        updateLocks.putIfAbsent(playerId, new AtomicBoolean(false));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder(false) instanceof SpawnerStackerHolder holder)) return;

        String spawnerId = holder.getSpawnerData().getSpawnerId();
        UUID playerId = player.getUniqueId();

        // Verify the player is really closing the GUI (not just inventory updates)
        Scheduler.runEntityTaskLater(player, () -> {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder(false) instanceof SpawnerStackerHolder)) {
                // Remove viewer and cancel any pending updates
                removeViewer(spawnerId, playerId);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cleanupPlayer(playerId);
    }

    // ---- Helper Methods ----

    private void cleanupPlayer(UUID playerId) {
        // Cancel any pending tasks - faster direct retrieval
        Scheduler.Task task = pendingUpdates.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Remove player from tracking - faster direct removal
        lastClickTime.remove(playerId);
        updateLocks.remove(playerId);

        // Remove from all viewer sets efficiently
        for (Map.Entry<String, Set<UUID>> entry : activeViewers.entrySet()) {
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                activeViewers.remove(entry.getKey());
            }
        }
    }

    public void cleanupAll() {
        // Cancel all pending tasks
        pendingUpdates.values().forEach(task -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        });

        // Clear all tracking maps at once
        pendingUpdates.clear();
        lastClickTime.clear();
        updateLocks.clear();
        activeViewers.clear();
        entityTypeCache.clear();
    }

    private void removeViewer(String spawnerId, UUID playerId) {
        Set<UUID> viewers = activeViewers.get(spawnerId);
        if (viewers != null) {
            viewers.remove(playerId);
            if (viewers.isEmpty()) {
                activeViewers.remove(spawnerId);
            }
        }

        // Cleanup player data
        Scheduler.Task task = pendingUpdates.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private int determineChangeAmount(int slotIndex) {
        // Optimized slot checking using binary search or direct lookup when possible
        if (slotIndex >= 9 && slotIndex <= 11) {
            // Decrease buttons
            return -STACK_AMOUNTS[slotIndex - 9];
        } else if (slotIndex >= 15 && slotIndex <= 17) {
            // Increase buttons
            return STACK_AMOUNTS[17 - slotIndex];
        }
        return 0; // Not a modification button
    }

    private void processStackModification(Player player, SpawnerData spawner, int changeAmount) {
        if (changeAmount < 0) {
            // Handle stack decrease
            handleStackDecrease(player, spawner, Math.abs(changeAmount));
        } else {
            // Handle stack increase
            handleStackIncrease(player, spawner, changeAmount);
        }
    }

    private void handleStackDecrease(Player player, SpawnerData spawner, int removeAmount) {
        Location location = spawner.getSpawnerLocation();

        // Acquire location-based lock to prevent race condition with pickaxe break
        // This ensures GUI destack and pickaxe break cannot happen simultaneously
        if (!locationLockManager.tryLock(location)) {
            // Another operation (likely pickaxe break) is in progress
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            // Re-read stack size after acquiring lock
            int currentSize = spawner.getStackSize();

            // Check if trying to go below 1 - fast path
            if (currentSize == 1) {
                messageService.sendMessage(player, "spawner_cannot_remove_last");
                return;
            }

            int targetSize = Math.max(1, currentSize - removeAmount);
            int actualChange = currentSize - targetSize;

            // Stop if no change - fast path
            if (actualChange <= 0) {
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("amount", String.valueOf(currentSize));
                messageService.sendMessage(player, "spawner_stacker_minimum_reached", placeholders);
                return;
            }

            // Check inventory capacity before removing from stack
            int inventoryCapacity = countAvailableSpawnerCapacity(player, spawner);
            if (inventoryCapacity <= 0) {
                messageService.sendMessage(player, "inventory_full");
                return;
            }
            actualChange = Math.min(actualChange, inventoryCapacity);
            targetSize = currentSize - actualChange;

            // Create final variables for lambda
            final int finalActualChange = actualChange;
            final int finalTargetSize = targetSize;

            if(SpawnerRemoveEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerRemoveEvent e = new SpawnerRemoveEvent(player, spawner.getSpawnerLocation(), finalTargetSize, finalActualChange);
                Bukkit.getPluginManager().callEvent(e);
                if (e.isCancelled()) return;
            }

            // Update stack size first (after capacity check, so it's safe)
            spawner.setStackSize(finalTargetSize);
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());

            // Give spawners to player after decreasing stack
            if (spawner.isItemSpawner()) {
                giveItemSpawnersToPlayer(player, finalActualChange, spawner.getSpawnedItemMaterial());
            } else {
                giveSpawnersToPlayer(player, finalActualChange, spawner.getEntityType());
            }

            // Log destack operation
            if (plugin.getSpawnerActionLogger() != null) {
                plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DESTACK_GUI, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                        .location(spawner.getSpawnerLocation())
                        .entityType(spawner.getEntityType())
                        .metadata("amount_removed", finalActualChange)
                        .metadata("old_stack_size", currentSize)
                        .metadata("new_stack_size", finalTargetSize)
                );
            }

            // Play sound
            player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
        } finally {
            locationLockManager.unlock(location);
        }
    }

    private void handleStackIncrease(Player player, SpawnerData spawner, int changeAmount) {
        int currentSize = spawner.getStackSize();
        int maxStackSize = spawner.getMaxStackSize();

        // Calculate how many more can be added
        int spaceLeft = maxStackSize - currentSize;

        // Fast path for full stack
        if (spaceLeft <= 0) {
            Map<String, String> placeholders = new HashMap<>(2);
            placeholders.put("max", String.valueOf(maxStackSize));
            messageService.sendMessage(player, "spawner_stack_full", placeholders);
            return;
        }

        // Limit change to available space
        int actualChange = Math.min(changeAmount, spaceLeft);

        // Analyze inventory based on spawner type
        InventoryScanResult scanResult;
        if (spawner.isItemSpawner()) {
            Material requiredItemMaterial = spawner.getSpawnedItemMaterial();
            scanResult = scanPlayerInventoryForItemSpawner(player, requiredItemMaterial);
        } else {
            EntityType requiredType = spawner.getEntityType();
            scanResult = scanPlayerInventory(player, requiredType);
        }

        // Check if player has different spawner types
        if (scanResult.availableSpawners == 0 && scanResult.hasDifferentType) {
            messageService.sendMessage(player, "spawner_different");
            return;
        }

        // Check if player has enough spawners
        if (scanResult.availableSpawners < actualChange) {
            Map<String, String> placeholders = new HashMap<>(4);
            placeholders.put("amountChange", String.valueOf(actualChange));
            placeholders.put("amountAvailable", String.valueOf(scanResult.availableSpawners));
            messageService.sendMessage(player, "spawner_insufficient_quantity", placeholders);
            return;
        }

        // Remove from inventory and update stack
        if(SpawnerStackEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerStackEvent e = new SpawnerStackEvent(player, spawner.getSpawnerLocation(), spawner.getStackSize(),
                    spawner.getStackSize() + actualChange, SpawnerStackEvent.StackSource.GUI, spawner.getEntityType());
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) return;
        }

        if (spawner.isItemSpawner()) {
            removeValidItemSpawnersFromInventory(player, spawner.getSpawnedItemMaterial(), actualChange, scanResult.spawnerSlots);
        } else {
            removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange, scanResult.spawnerSlots);
        }
        spawner.setStackSize(currentSize + actualChange);

        // Mark spawner as modified for database save
        spawnerManager.markSpawnerModified(spawner.getSpawnerId());

        // Notify if max stack reached
        if (actualChange < changeAmount) {
            Map<String, String> placeholders = new HashMap<>(2);
            placeholders.put("amount", String.valueOf(actualChange));
            messageService.sendMessage(player, "spawner_stacker_minimum_reached", placeholders);
        }

        // Play sound
        player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    private void handleAddAll(Player player, SpawnerData spawner) {
        int currentSize = spawner.getStackSize();
        int maxStackSize = spawner.getMaxStackSize();
        int spaceLeft = maxStackSize - currentSize;

        if (spaceLeft <= 0) {
            Map<String, String> placeholders = new HashMap<>(2);
            placeholders.put("max", String.valueOf(maxStackSize));
            messageService.sendMessage(player, "spawner_stack_full", placeholders);
            return;
        }

        // Scan inventory for matching spawners
        InventoryScanResult scanResult;
        if (spawner.isItemSpawner()) {
            scanResult = scanPlayerInventoryForItemSpawner(player, spawner.getSpawnedItemMaterial());
        } else {
            scanResult = scanPlayerInventory(player, spawner.getEntityType());
        }

        if (scanResult.availableSpawners == 0 && scanResult.hasDifferentType) {
            messageService.sendMessage(player, "spawner_different");
            return;
        }

        if (scanResult.availableSpawners == 0) {
            messageService.sendMessage(player, "spawner_insufficient_quantity",
                Map.of("amountChange", "1", "amountAvailable", "0"));
            return;
        }

        int actualChange = Math.min(spaceLeft, scanResult.availableSpawners);

        if (SpawnerStackEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerStackEvent e = new SpawnerStackEvent(player, spawner.getSpawnerLocation(), spawner.getStackSize(),
                    spawner.getStackSize() + actualChange, SpawnerStackEvent.StackSource.GUI, spawner.getEntityType());
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled())
                return;
        }

        // Remove spawners from inventory first
        if (spawner.isItemSpawner()) {
            removeValidItemSpawnersFromInventory(player, spawner.getSpawnedItemMaterial(), actualChange,
                    scanResult.spawnerSlots);
        } else {
            removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange,
                    scanResult.spawnerSlots);
        }

        // Update stack size after removing from inventory
        spawner.setStackSize(currentSize + actualChange);
        spawnerManager.markSpawnerModified(spawner.getSpawnerId());

        player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    private void handleRemoveAll(Player player, SpawnerData spawner) {
        Location location = spawner.getSpawnerLocation();

        if (!locationLockManager.tryLock(location)) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        try {
            int currentSize = spawner.getStackSize();

            if (currentSize == 1) {
                messageService.sendMessage(player, "spawner_cannot_remove_last");
                return;
            }

            int actualChange = currentSize - 1;

            // Cap at available inventory capacity
            int inventoryCapacity = countAvailableSpawnerCapacity(player, spawner);
            if (inventoryCapacity <= 0) {
                messageService.sendMessage(player, "inventory_full");
                return;
            }
            actualChange = Math.min(actualChange, inventoryCapacity);
            int newStackSize = currentSize - actualChange;

            // Create final variables for lambda
            final int finalActualChange = actualChange;
            final int finalNewStackSize = newStackSize;

            if (SpawnerRemoveEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerRemoveEvent e = new SpawnerRemoveEvent(player, spawner.getSpawnerLocation(), finalNewStackSize,
                        finalActualChange);
                Bukkit.getPluginManager().callEvent(e);
                if (e.isCancelled())
                    return;
            }

            // Update stack size first (after capacity check, so it's safe)
            spawner.setStackSize(finalNewStackSize);
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());

            // Give spawners to player after decreasing stack
            if (spawner.isItemSpawner()) {
                giveItemSpawnersToPlayer(player, finalActualChange, spawner.getSpawnedItemMaterial());
            } else {
                giveSpawnersToPlayer(player, finalActualChange, spawner.getEntityType());
            }

            if (plugin.getSpawnerActionLogger() != null) {
                plugin.getSpawnerActionLogger().log(
                        github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DESTACK_GUI,
                        builder -> builder.player(player.getName(), player.getUniqueId())
                                .location(spawner.getSpawnerLocation())
                                .entityType(spawner.getEntityType())
                                .metadata("amount_removed", finalActualChange)
                                .metadata("old_stack_size", currentSize)
                                .metadata("new_stack_size", finalNewStackSize));
            }

            player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
        } finally {
            locationLockManager.unlock(location);
        }
    }

    /**
     * Counts how many spawners of the given type the player's inventory can accept.
     * Considers both empty slots and partial stacks of matching spawners.
     */
    private int countAvailableSpawnerCapacity(Player player, SpawnerData spawner) {
        final int MAX_STACK_SIZE = 64;
        int capacity = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < 36; i++) { // Main inventory slots 0-35
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                capacity += MAX_STACK_SIZE;
                continue;
            }

            if (item.getType() == Material.SPAWNER && !SpawnerTypeChecker.isVanillaSpawner(item)) {
                Optional<EntityType> itemEntityType = getSpawnerEntityTypeCached(item);
                boolean matches;
                if (spawner.isItemSpawner()) {
                    // For item spawners, match by spawned item material
                    matches = false; // Item spawners use separate give method; empty slots cover them
                } else {
                    matches = itemEntityType.isPresent() && itemEntityType.get() == spawner.getEntityType();
                }

                if (matches && item.getAmount() < MAX_STACK_SIZE) {
                    capacity += MAX_STACK_SIZE - item.getAmount();
                }
            }
        }

        return capacity;
    }

    private void scheduleViewersUpdate(SpawnerData spawner) {
        String spawnerId = spawner.getSpawnerId();
        Set<UUID> viewers = activeViewers.get(spawnerId);
        if (viewers == null || viewers.isEmpty()) return;

        // Use a shared task for all viewers of this spawner
        Scheduler.Task task = Scheduler.runTaskLater(() -> {
            updateAllViewers(spawner, viewers);
        }, UPDATE_DELAY);

        // Store the task for potential cancellation
        for (UUID viewerId : viewers) {
            pendingUpdates.put(viewerId, task);
        }
    }

    private void updateAllViewers(SpawnerData spawner, Set<UUID> viewers) {
        // Update all players viewing this spawner
        for (UUID viewerId : viewers) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                AtomicBoolean lock = updateLocks.get(viewerId);
                if (lock != null && lock.compareAndSet(false, true)) {
                    try {
                        updateGui(viewer, spawner);
                    } finally {
                        lock.set(false);
                    }
                }
            }
        }
    }

    private void updateGui(Player player, SpawnerData spawner) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!(inv.getHolder(false) instanceof SpawnerStackerHolder)) return;

        // Pre-create placeholders map once
        Map<String, String> basePlaceholders = createBasePlaceholders(spawner);

        // Update spawner info in slot 13
        updateInfoItem(inv, basePlaceholders);

        // Update decrease and increase buttons
        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            updateActionButton(inv, "remove", STACK_AMOUNTS[i], DECREASE_SLOTS[i], basePlaceholders);
        }

        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            updateActionButton(inv, "add", STACK_AMOUNTS[i], INCREASE_SLOTS[i], basePlaceholders);
        }

        // Update "all" buttons
        updateAllActionButton(inv, "remove_all", REMOVE_ALL_SLOT, basePlaceholders);
        updateAllActionButton(inv, "add_all", ADD_ALL_SLOT, basePlaceholders);

        // Force client refresh
        player.updateInventory();
    }

    private Map<String, String> createBasePlaceholders(SpawnerData spawner) {
        Map<String, String> placeholders = new HashMap<>(8);
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("max_stack_size", String.valueOf(spawner.getMaxStackSize()));

        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));

        return placeholders;
    }

    private void updateInfoItem(Inventory inventory, Map<String, String> basePlaceholders) {
        ItemStack infoItem = inventory.getItem(SPAWNER_INFO_SLOT);
        if (infoItem == null || !infoItem.hasItemMeta()) return;

        ItemMeta meta = infoItem.getItemMeta();

        // Create a copy of base placeholders
        Map<String, String> placeholders = new HashMap<>(basePlaceholders);

        // Get name and lore from the LanguageManager
        String name = languageManager.getGuiItemName("button_spawner.name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_spawner.lore", placeholders);

        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        infoItem.setItemMeta(meta);
    }

    private void updateActionButton(Inventory inventory, String action, int amount, int slot, Map<String, String> basePlaceholders) {
        ItemStack button = inventory.getItem(slot);
        if (button == null || !button.hasItemMeta()) return;

        ItemMeta meta = button.getItemMeta();

        // Create a copy of base placeholders and add action-specific ones
        Map<String, String> placeholders = new HashMap<>(basePlaceholders);
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("plural", amount > 1 ? "s" : "");

        // Get name and lore from the LanguageManager
        String name = languageManager.getGuiItemName("button_" + action + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_" + action + ".lore", placeholders);

        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    private void updateAllActionButton(Inventory inventory, String action, int slot,
            Map<String, String> basePlaceholders) {
        ItemStack button = inventory.getItem(slot);
        if (button == null || !button.hasItemMeta())
            return;

        ItemMeta meta = button.getItemMeta();
        Map<String, String> placeholders = new HashMap<>(basePlaceholders);

        String name = languageManager.getGuiItemName("button_" + action + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_" + action + ".lore", placeholders);

        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    // Combined inventory scan that collects all required data in one pass
    private InventoryScanResult scanPlayerInventory(Player player, EntityType requiredType) {
        int count = 0;
        boolean hasDifferentType = false;
        List<SpawnerSlot> spawnerSlots = new ArrayList<>();

        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SPAWNER) continue;

            // Skip vanilla spawners
            if (SpawnerTypeChecker.isVanillaSpawner(item)) continue;

            Optional<EntityType> itemType = getSpawnerEntityTypeCached(item);
            if (itemType.isPresent()) {
                if (itemType.get() == requiredType) {
                    count += item.getAmount();
                    spawnerSlots.add(new SpawnerSlot(i, item.getAmount()));
                } else {
                    hasDifferentType = true;
                }
            }
        }

        return new InventoryScanResult(count, hasDifferentType, spawnerSlots);
    }

    // Combined inventory scan for item spawners that checks item material
    private InventoryScanResult scanPlayerInventoryForItemSpawner(Player player, Material requiredItemMaterial) {
        int count = 0;
        boolean hasDifferentType = false;
        List<SpawnerSlot> spawnerSlots = new ArrayList<>();

        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SPAWNER) continue;

            // Skip vanilla spawners
            if (SpawnerTypeChecker.isVanillaSpawner(item)) continue;

            // Check if it's an item spawner
            if (SpawnerTypeChecker.isItemSpawner(item)) {
                Material itemMaterial = SpawnerTypeChecker.getItemSpawnerMaterial(item);
                if (itemMaterial == requiredItemMaterial) {
                    count += item.getAmount();
                    spawnerSlots.add(new SpawnerSlot(i, item.getAmount()));
                } else {
                    hasDifferentType = true;
                }
            } else {
                // Regular spawner when we need item spawner
                hasDifferentType = true;
            }
        }

        return new InventoryScanResult(count, hasDifferentType, spawnerSlots);
    }

    private Optional<EntityType> getSpawnerEntityTypeCached(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return Optional.empty();
        }

        // Check cache first
        Optional<EntityType> cachedType = entityTypeCache.get(item);
        if (cachedType != null) {
            return cachedType;
        }

        // Extract type from metadata
        Optional<EntityType> result = getEntityTypeFromItem(item);

        // Cache the result
        entityTypeCache.put(item, result);
        return result;
    }

    public Optional<EntityType> getEntityTypeFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        // Get entity type from block state (most reliable method)
        if (meta instanceof BlockStateMeta blockMeta) {
            if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner handSpawner) {
                EntityType entityType = handSpawner.getSpawnedType();
                if (entityType != null) {
                    return Optional.of(entityType);
                }
            }
        }

        return Optional.empty();
    }

    private void removeValidSpawnersFromInventory(Player player, EntityType requiredType, int amountToRemove, List<SpawnerSlot> spawnerSlots) {
        int remainingToRemove = amountToRemove;

        // Use the pre-scanned slots for faster removal
        for (SpawnerSlot slot : spawnerSlots) {
            if (remainingToRemove <= 0) break;

            ItemStack item = player.getInventory().getItem(slot.slotIndex);
            // Verify item is still valid
            if (item == null || item.getType() != Material.SPAWNER) continue;

            Optional<EntityType> spawnerType = getSpawnerEntityTypeCached(item);
            if (spawnerType.isPresent() && spawnerType.get() == requiredType) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remainingToRemove) {
                    player.getInventory().setItem(slot.slotIndex, null);
                    remainingToRemove -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
            }
        }

        player.updateInventory();
    }

    private void removeValidItemSpawnersFromInventory(Player player, Material requiredItemMaterial, int amountToRemove, List<SpawnerSlot> spawnerSlots) {
        int remainingToRemove = amountToRemove;

        // Use the pre-scanned slots for faster removal
        for (SpawnerSlot slot : spawnerSlots) {
            if (remainingToRemove <= 0) break;

            ItemStack item = player.getInventory().getItem(slot.slotIndex);
            // Verify item is still valid
            if (item == null || item.getType() != Material.SPAWNER) continue;

            if (SpawnerTypeChecker.isItemSpawner(item)) {
                Material itemMaterial = SpawnerTypeChecker.getItemSpawnerMaterial(item);
                if (itemMaterial == requiredItemMaterial) {
                    int itemAmount = item.getAmount();
                    if (itemAmount <= remainingToRemove) {
                        player.getInventory().setItem(slot.slotIndex, null);
                        remainingToRemove -= itemAmount;
                    } else {
                        item.setAmount(itemAmount - remainingToRemove);
                        remainingToRemove = 0;
                    }
                }
            }
        }

        player.updateInventory();
    }

    public void giveSpawnersToPlayer(Player player, int amount, EntityType entityType) {
        final int MAX_STACK_SIZE = 64;
        int remainingAmount = amount;

        // First pass: Try to merge with existing non-vanilla stacks
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            // Skip null items, non-spawners, or vanilla spawners
            if (item == null || item.getType() != Material.SPAWNER || SpawnerTypeChecker.isVanillaSpawner(item)) {
                continue;
            }

            Optional<EntityType> itemEntityType = getSpawnerEntityTypeCached(item);
            if (itemEntityType.isEmpty() || itemEntityType.get() != entityType) continue;

            int currentAmount = item.getAmount();
            if (currentAmount < MAX_STACK_SIZE) {
                int canAdd = Math.min(MAX_STACK_SIZE - currentAmount, remainingAmount);
                item.setAmount(currentAmount + canAdd);
                remainingAmount -= canAdd;
            }
        }

        // Second pass: Create new stacks for remaining items
        if (remainingAmount > 0) {
            // Create all stacks at once to avoid multiple inventory operations
            List<ItemStack> newStacks = new ArrayList<>();

            while (remainingAmount > 0) {
                int stackSize = Math.min(MAX_STACK_SIZE, remainingAmount);
                ItemStack spawnerItem = spawnerItemFactory.createSmartSpawnerItem(entityType, stackSize);
                newStacks.add(spawnerItem);
                remainingAmount -= stackSize;
            }

            // Try to add all at once
            boolean allFit = true;
            for (ItemStack stack : newStacks) {
                boolean addedSuccessfully = addItemAvoidingVanillaSpawners(player, stack);
                if (!addedSuccessfully) {
                    // Drop any items that couldn't fit
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                    allFit = false;
                }
            }

            if (!allFit) {
                messageService.sendMessage(player, "inventory_full");
            }
        }

        // Update inventory
        player.updateInventory();
    }

    public void giveItemSpawnersToPlayer(Player player, int amount, Material itemMaterial) {
        final int MAX_STACK_SIZE = 64;
        int remainingAmount = amount;

        // First pass: Try to merge with existing item spawner stacks
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            // Skip null items, non-spawners, vanilla spawners, or regular spawners
            if (item == null || item.getType() != Material.SPAWNER ||
                SpawnerTypeChecker.isVanillaSpawner(item) ||
                !SpawnerTypeChecker.isItemSpawner(item)) {
                continue;
            }

            Material itemSpawnerMaterial = SpawnerTypeChecker.getItemSpawnerMaterial(item);
            if (itemSpawnerMaterial != itemMaterial) continue;

            int currentAmount = item.getAmount();
            if (currentAmount < MAX_STACK_SIZE) {
                int canAdd = Math.min(MAX_STACK_SIZE - currentAmount, remainingAmount);
                item.setAmount(currentAmount + canAdd);
                remainingAmount -= canAdd;
            }
        }

        // Second pass: Create new stacks for remaining items
        if (remainingAmount > 0) {
            // Create all stacks at once to avoid multiple inventory operations
            List<ItemStack> newStacks = new ArrayList<>();

            while (remainingAmount > 0) {
                int stackSize = Math.min(MAX_STACK_SIZE, remainingAmount);
                ItemStack spawnerItem = spawnerItemFactory.createItemSpawnerItem(itemMaterial, stackSize);
                newStacks.add(spawnerItem);
                remainingAmount -= stackSize;
            }

            // Try to add all at once
            boolean allFit = true;
            for (ItemStack stack : newStacks) {
                boolean addedSuccessfully = addItemAvoidingVanillaSpawners(player, stack);
                if (!addedSuccessfully) {
                    // Drop any items that couldn't fit
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                    allFit = false;
                }
            }

            if (!allFit) {
                messageService.sendMessage(player, "inventory_full");
            }
        }

        // Update inventory
        player.updateInventory();
    }

    private boolean addItemAvoidingVanillaSpawners(Player player, ItemStack item) {
        // If it's not a spawner, just use the normal method
        if (item.getType() != Material.SPAWNER) {
            Map<Integer, ItemStack> failed = player.getInventory().addItem(item);
            return failed.isEmpty();
        }

        // For spawners, find an empty slot in the main inventory
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {  // Main inventory slots are 0-35
            ItemStack currentItem = inv.getItem(i);
            if (currentItem == null) {
                // Found an empty slot in main inventory
                inv.setItem(i, item.clone());
                return true;
            }
        }

        // No empty slots found in main inventory
        return false;
    }

    public void closeAllViewersInventory(String spawnerId) {
        Set<UUID> viewers = activeViewers.get(spawnerId);
        if (viewers == null || viewers.isEmpty()) return;

        // Create a copy to avoid concurrent modification
        Set<UUID> viewersCopy = new HashSet<>(viewers);

        // Close inventory for each player
        for (UUID viewerId : viewersCopy) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory();
            }
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    private boolean canUseStacker(Player player, SpawnerData spawner) {
        if (player.hasPermission(DROP_CHANCE_BYPASS_PERMISSION)) {
            return true;
        }

        SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
        return settingsConfig == null || !settingsConfig.hasSpawnerDropChance(spawner.getEntityType());
    }
}
