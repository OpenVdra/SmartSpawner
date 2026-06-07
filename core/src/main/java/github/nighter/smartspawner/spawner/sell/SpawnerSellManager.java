package github.nighter.smartspawner.spawner.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.api.events.SpawnerSellEvent;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;


public class SpawnerSellManager {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    public SpawnerSellManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
    }

    /**
     * Sells all items from the spawner's virtual inventory.
     * Convenience overload with no completion callback.
     */
    public void sellAllItems(Player player, SpawnerData spawner) {
        sellAllItems(player, spawner, null);
    }

    /**
     * Sells all items from the spawner's virtual inventory.
     *
     * Threading model (Folia-safe):
     * 1. CAS on {@code spawner.startSelling()} – single atomic guard, no nested locks.
     * 2. Close all GUI viewers immediately (caller is already on the region/main thread).
     * 3. Snapshot consolidated items + accumulated sell value (safe: isSelling blocks all
     *    concurrent inventory mutations from loot-gen, break, and stack operations).
     * 4. Async thread: calculate {@link SellResult} – pure CPU, no Bukkit API.
     * 5. Location thread (Folia region / Paper main): apply deposit + item removal + notifications.
     * 6. {@code onComplete.run()} called on the location thread after step 5, before stopSelling().
     * 7. {@code spawner.stopSelling()} released in the finally block of step 5.
     *
     * If the sell cannot be initiated (already selling, empty inventory), {@code onComplete} is
     * invoked synchronously on the calling thread so the caller can always do cleanup.
     *
     * @param onComplete optional callback, runs on the spawner's region/main thread after sell
     *                   completes (success or failure that got past the CAS). Never called if
     *                   the sell was outright rejected (CAS failed / empty).
     */
    public void sellAllItems(Player player, SpawnerData spawner, Runnable onComplete) {
        // Single atomic guard – prevents race conditions and double-sell exploits
        if (!spawner.startSelling()) {
            messageService.sendMessage(player, "action_in_progress");
            // Notify caller even on rejection so it can do its own cleanup
            if (onComplete != null) onComplete.run();
            return;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Quick empty-check before any real work
        if (virtualInv.getUsedSlots() == 0) {
            spawner.stopSelling();
            messageService.sendMessage(player, "spawner_storage_empty");
            if (onComplete != null) onComplete.run();
            return;
        }

        // Recalculate sell value if the price cache is stale (rare)
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }

        // Kick all viewers out while the sell is running
        spawnerGuiViewManager.closeAllViewersInventory(spawner);

        // Lightweight snapshot – safe because isSelling prevents concurrent inventory changes
        final Map<ItemSignature, Long> itemSnapshot = virtualInv.getConsolidatedItems();
        final double accumulatedValue = spawner.getAccumulatedSellValue();
        final Location spawnerLocation = spawner.getSpawnerLocation();

        // Async: pure CPU computation, no Bukkit API
        Scheduler.runTaskAsync(() -> {
            SellResult result;
            try {
                result = calculateSellValue(itemSnapshot, accumulatedValue);
            } catch (Exception e) {
                plugin.getLogger().warning("Sell calculation error for " + player.getName() + ": " + e.getMessage());
                Scheduler.runLocationTask(spawnerLocation, () -> {
                    try {
                        if (onComplete != null) onComplete.run();
                    } finally {
                        spawner.stopSelling();
                    }
                    messageService.sendMessage(player, "action_failed");
                });
                return;
            }

            // Apply on the location's region thread (Folia) or the main thread (Paper)
            Scheduler.runLocationTask(spawnerLocation, () -> {
                try {
                    applySellResult(player, spawner, result);
                } finally {
                    // onComplete MUST run in finally so activeSells is always cleared,
                    // even when applySellResult throws (e.g. economy plugin error).
                    try {
                        if (onComplete != null) onComplete.run();
                    } finally {
                        spawner.stopSelling();
                    }
                }
            });
        });
        // stopSelling() ownership is transferred to the async chain above
    }

    /**
     * Applies the sell result on the spawner's region/main thread.
     * Called while {@code spawner.isSelling()} is true; {@code stopSelling()} is the caller's
     * responsibility via the surrounding finally block.
     */
    private void applySellResult(Player player, SpawnerData spawner, SellResult sellResult) {
        if (!sellResult.isSuccessful()) {
            messageService.sendMessage(player, "no_sellable_items");
            return;
        }

        double amount = sellResult.getTotalValue();

        // Fire the cancellable API event
        if (SpawnerSellEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerSellEvent event = new SpawnerSellEvent(
                    player, spawner.getSpawnerLocation(), sellResult.getItemsToRemove(), amount, spawner.getEntityType());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            if (event.getMoneyAmount() >= 0) amount = event.getMoneyAmount();
        }

        // Deposit money first
        boolean depositSuccess = plugin.getItemPriceManager().getCurrencyManager().deposit(amount, player);
        if (!depositSuccess) {
            messageService.sendMessage(player, "action_failed");
            return;
        }

        // Remove items – if removal somehow fails (should never happen under isSelling guard),
        // items are simply lost; no rollback. Attempting to dupe results in item loss.
        spawner.removeItemsAndUpdateSellValue(sellResult.getItemsToRemove());

        // Update spawner state
        spawner.updateHologramData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (spawner.getIsAtCapacity() && virtualInv.getUsedSlots() < spawner.getMaxSpawnerLootSlots()) {
            spawner.setIsAtCapacity(false);
        }

        // Invalidate GUI caches so the next open shows fresh data
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());

        // Notify the player
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", plugin.getLanguageManager().formatNumber(sellResult.getItemsSold()));
        placeholders.put("price", plugin.getLanguageManager().formatNumber(amount));
        messageService.sendMessage(player, "sell_success", placeholders);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        spawner.markLastSellAsProcessed();
    }

    /**
     * Calculates the total sell value and constructs the list of {@link ItemStack}s to remove.
     * Pure computation – no Bukkit API calls, safe to run on an async thread.
     */
    private SellResult calculateSellValue(Map<ItemSignature, Long> consolidatedItems,
                                          double totalValue) {
        long totalItemsSold = 0;
        ArrayList<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            ItemSignature signature = entry.getKey();
            long amount = entry.getValue();
            int maxStackSize = signature.getMaxStackSize();

            totalItemsSold += amount;

            int stacksNeeded = (int) Math.ceil((double) amount / maxStackSize);
            itemsToRemove.ensureCapacity(itemsToRemove.size() + stacksNeeded);

            long remaining = amount;
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                stack.setAmount((int) Math.min(remaining, maxStackSize));
                itemsToRemove.add(stack);
                remaining -= stack.getAmount();
            }
        }

        return new SellResult(totalValue, totalItemsSold, itemsToRemove);
    }
}
