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
        sellAllItems(player, spawner, null, 0, 0);
    }

    /**
     * Sells all items from the spawner's virtual inventory.
     * Convenience overload with completion callback but no exp data.
     */
    public void sellAllItems(Player player, SpawnerData spawner, Runnable onComplete) {
        sellAllItems(player, spawner, onComplete, 0, 0);
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
     * @param onComplete  optional callback, runs on the spawner's region/main thread after sell
     *                    completes (success or failure that got past the CAS). Never called if
     *                    the sell was outright rejected (CAS failed / empty).
     * @param expCollected total exp that was already silently collected before this sell (for
     *                     combined sell+exp message). Pass 0 if no exp was collected.
     * @param expMending   amount of exp consumed by Mending (out of expCollected). Pass 0 if none.
     */
    public void sellAllItems(Player player, SpawnerData spawner, Runnable onComplete, long expCollected, long expMending) {
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
                applySellResult(player, spawner, result, expCollected, expMending);
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
    }

    /**
     * Applies the sell result on the spawner's region/main thread.
     * Called while {@code spawner.isSelling()} is true; {@code stopSelling()} is the caller's
     * responsibility via the surrounding finally block.
     *
     * @param expCollected total exp already silently collected (0 = none / regular sell only)
     * @param expMending   amount of exp consumed by Mending
     */
    private void applySellResult(Player player, SpawnerData spawner, SellResult sellResult, long expCollected, long expMending) {
        if (!sellResult.isSuccessful()) {
            messageService.sendMessage(player, "no_sellable_items");
            return;
        }

        double amount = sellResult.getTotalValue();

        // Fire the cancellable API event
        if (SpawnerSellEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerSellEvent event = new SpawnerSellEvent(
                    player,
                    spawner.getSpawnerLocation(),
                    toApiItemStacks(sellResult.getItemsToRemove()),
                    amount,
                    spawner.getEntityType());
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

        if (expCollected > 0) {
            long expGiven = expCollected - expMending;
            placeholders.put("exp", plugin.getLanguageManager().formatNumber(expGiven));
            if (expMending > 0) {
                placeholders.put("exp_mending", plugin.getLanguageManager().formatNumber(expMending));
                messageService.sendMessage(player, "sell_and_exp_success_with_mending", placeholders);
            } else {
                messageService.sendMessage(player, "sell_and_exp_success", placeholders);
            }
        } else {
            messageService.sendMessage(player, "sell_success", placeholders);
        }
        spawner.markLastSellAsProcessed();
    }

    /**
     * Calculates the total sell value and records the consolidated item signatures to remove.
     * Pure computation – no Bukkit API calls, safe to run on an async thread.
     */
    private SellResult calculateSellValue(Map<ItemSignature, Long> consolidatedItems, double totalValue) {
        long totalItemsSold = 0;

        for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            totalItemsSold += entry.getValue();
        }

        return new SellResult(totalValue, totalItemsSold, consolidatedItems);
    }

    private List<ItemStack> toApiItemStacks(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> apiItems = new ArrayList<>(items.size());

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amount = entry.getValue();

            ItemStack stack = signature.getTemplate();
            stack.setAmount((int) Math.min(amount, Integer.MAX_VALUE));
            apiItems.add(stack);
        }

        return apiItems;
    }
}
