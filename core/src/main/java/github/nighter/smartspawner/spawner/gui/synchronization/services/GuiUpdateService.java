package github.nighter.smartspawner.spawner.gui.synchronization.services;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for updating GUI items (storage, exp, spawner info).
 * Handles batched updates and item synchronization with thread safety.
 */
public class GuiUpdateService {

    // Update flags - using bit flags for efficient state tracking
    public static final int UPDATE_CHEST = 1;
    public static final int UPDATE_INFO = 2;
    public static final int UPDATE_EXP = 4;
    public static final int UPDATE_ALL = UPDATE_CHEST | UPDATE_INFO | UPDATE_EXP;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;

    // Batched update tracking
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> updateFlags = new ConcurrentHashMap<>();

    public GuiUpdateService(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
    }

    /**
     * Schedules a GUI update for a player.
     *
     * @param playerId The player's UUID
     * @param flags Update flags indicating which items to update
     */
    public void scheduleUpdate(UUID playerId, int flags) {
        pendingUpdates.add(playerId);
        updateFlags.put(playerId, flags);
    }

    /**
     * Processes all pending GUI updates.
     * Called periodically by the update task.
     *
     * @param viewerInfoGetter Function to get viewer info
     * @param untrackViewer Consumer to untrack a viewer
     */
    public void processPendingUpdates(
            java.util.function.Function<UUID, ?> viewerInfoGetter,
            java.util.function.Consumer<UUID> untrackViewer) {
        
        if (pendingUpdates.isEmpty()) {
            return;
        }

        Set<UUID> currentUpdates = new HashSet<>(pendingUpdates);
        pendingUpdates.clear();

        for (UUID playerId : currentUpdates) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                untrackViewer.accept(playerId);
                updateFlags.remove(playerId);
                continue;
            }

            Object info = viewerInfoGetter.apply(playerId);
            if (info == null) {
                updateFlags.remove(playerId);
                continue;
            }

            int flags = updateFlags.getOrDefault(playerId, UPDATE_ALL);
            updateFlags.remove(playerId);

            Location loc = player.getLocation();
            final int finalFlags = flags;

            // Extract spawner data from viewer info using reflection-like approach
            SpawnerData spawner = extractSpawnerData(info);
            if (spawner == null) {
                continue;
            }

            Scheduler.runLocationTask(loc, () -> {
                if (!player.isOnline()) {
                    return;
                }

                Inventory openInv = player.getOpenInventory().getTopInventory();
                if (!(openInv.getHolder(false) instanceof SpawnerMenuHolder)) {
                    return;
                }

                processInventoryUpdate(player, openInv, spawner, finalFlags);
            });
        }
    }

    /**
     * Processes inventory update for a specific player.
     */
    private void processInventoryUpdate(Player player, Inventory inventory, SpawnerData spawner, int flags) {
        boolean needsUpdate = false;

        SpawnerMenuHolder holder = (SpawnerMenuHolder) inventory.getHolder(false);

        if ((flags & UPDATE_CHEST) != 0) {
            GuiButton storageButton = holder.getStorageButton();
            if (storageButton != null) {
                needsUpdate |= updateChestItem(inventory, spawner, storageButton);
            }
        }

        if ((flags & UPDATE_INFO) != 0) {
            GuiButton infoButton = holder.getInfoButton();
            if (infoButton != null) {
                needsUpdate |= updateSpawnerInfoItem(inventory, spawner, player, infoButton);
            }
        }

        if ((flags & UPDATE_EXP) != 0) {
            GuiButton expButton = holder.getExpButton();
            if (expButton != null) {
                needsUpdate |= updateExpItem(inventory, spawner, expButton);
            }
        }

        if (needsUpdate) {
            player.updateInventory();
        }
    }

    /**
     * Updates the chest/storage item in inventory.
     */
    private boolean updateChestItem(Inventory inventory, SpawnerData spawner, GuiButton button) {
        int storageSlot = button.getSlot();
        if (storageSlot < 0) {
            return false;
        }

        ItemStack currentChestItem = inventory.getItem(storageSlot);
        ItemStack newChestItem = spawnerMenuUI.createLootStorageItem(spawner, button);

        if (areItemsEqual(currentChestItem, newChestItem)) {
            return false;
        }
        inventory.setItem(storageSlot, newChestItem);
        return true;
    }

    /**
     * Updates the exp item in inventory.
     */
    private boolean updateExpItem(Inventory inventory, SpawnerData spawner, GuiButton button) {
        int expSlot = button.getSlot();
        if (expSlot < 0) {
            return false;
        }

        ItemStack currentExpItem = inventory.getItem(expSlot);
        ItemStack newExpItem = spawnerMenuUI.createExpItem(spawner, button);

        if (areItemsEqual(currentExpItem, newExpItem)) {
            return false;
        }
        inventory.setItem(expSlot, newExpItem);
        return true;
    }

    /**
     * Updates the spawner info item in inventory.
     */
    private boolean updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner,
                                          Player player, GuiButton button) {
        int spawnerInfoSlot = button.getSlot();
        if (spawnerInfoSlot < 0) {
            return false;
        }

        ItemStack currentSpawnerItem = inventory.getItem(spawnerInfoSlot);
        ItemStack newSpawnerItem = spawnerMenuUI.createSpawnerInfoItem(player, spawner, button);

        if (areItemsEqual(currentSpawnerItem, newSpawnerItem)) {
            return false;
        }
        preserveTimerInfo(currentSpawnerItem, newSpawnerItem);
        inventory.setItem(spawnerInfoSlot, newSpawnerItem);
        return true;
    }

    /**
     * Preserves timer information when updating spawner info item.
     */
    private void preserveTimerInfo(ItemStack currentItem, ItemStack newItem) {
        if (currentItem == null || newItem == null) {
            return;
        }
        ItemMeta currentMeta = currentItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();

        if (currentMeta == null || !currentMeta.hasLore() || newMeta == null || !newMeta.hasLore()) {
            return;
        }

        List<String> currentLore = currentMeta.getLore();
        List<String> newLore = newMeta.getLore();

        if (currentLore == null || newLore == null) {
            return;
        }

        // Find timer line in new lore
        int newTimerLineIndex = -1;
        for (int i = 0; i < newLore.size(); i++) {
            if (newLore.get(i).contains("{time}")) {
                newTimerLineIndex = i;
                break;
            }
        }

        if (newTimerLineIndex == -1) {
            return;
        }

        // Extract timer value from current lore
        if (newTimerLineIndex < currentLore.size()) {
            String currentLine = currentLore.get(newTimerLineIndex);
            String newLine = newLore.get(newTimerLineIndex);

            if (!currentLine.contains("{time}") && newLine.contains("{time}")) {
                String currentTimerValue = extractTimerValue(currentLine, newLine);
                
                if (currentTimerValue != null && !currentTimerValue.isEmpty()) {
                    Map<String, String> timerPlaceholder = Collections.singletonMap("time", currentTimerValue);
                    List<String> updatedLore = new ArrayList<>(newLore.size());

                    for (String line : newLore) {
                        updatedLore.add(languageManager.applyOnlyPlaceholders(line, timerPlaceholder));
                    }

                    newMeta.setLore(updatedLore);
                    newItem.setItemMeta(newMeta);
                }
            }
        }
    }

    /**
     * Compares two ItemStacks, including amount and all item metadata.
     *
     * @param item1 First ItemStack to compare
     * @param item2 Second ItemStack to compare
     * @return true if the items are equivalent
     */
    public static boolean areItemsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == item2) {
            return true;
        }
        if (item1 == null || item2 == null) {
            return false;
        }
        return item1.getAmount() == item2.getAmount() && item1.isSimilar(item2);
    }

    /**
     * Extracts timer value from current line using template matching.
     */
    private String extractTimerValue(String currentLine, String newLine) {
        String newLineTemplate = newLine.replace("{time}", "TIMER_PLACEHOLDER");
        String cleanNewTemplate = ChatColor.stripColor(newLineTemplate);
        String cleanCurrentLine = ChatColor.stripColor(currentLine);

        int placeholderIndex = cleanNewTemplate.indexOf("TIMER_PLACEHOLDER");
        if (placeholderIndex >= 0 && cleanCurrentLine.length() >= placeholderIndex) {
            String beforePlaceholder = cleanNewTemplate.substring(0, placeholderIndex);
            String afterPlaceholder = cleanNewTemplate.substring(placeholderIndex + "TIMER_PLACEHOLDER".length());

            if (cleanCurrentLine.startsWith(beforePlaceholder) && cleanCurrentLine.endsWith(afterPlaceholder)) {
                int startIndex = beforePlaceholder.length();
                int endIndex = cleanCurrentLine.length() - afterPlaceholder.length();
                if (endIndex > startIndex) {
                    return cleanCurrentLine.substring(startIndex, endIndex).trim();
                }
            }
        }

        return null;
    }

    /**
     * Extracts SpawnerData from viewer info object.
     */
    private SpawnerData extractSpawnerData(Object info) {
        if (info instanceof github.nighter.smartspawner.spawner.gui.synchronization.managers.ViewerTrackingManager.ViewerInfo viewerInfo) {
            return viewerInfo.getSpawnerData();
        }
        return null;
    }

    /**
     * Clears all pending updates.
     */
    public void clearAllPendingUpdates() {
        pendingUpdates.clear();
        updateFlags.clear();
    }

    /**
     * Clears pending updates for a specific player.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerUpdates(UUID playerId) {
        pendingUpdates.remove(playerId);
        updateFlags.remove(playerId);
    }
}
