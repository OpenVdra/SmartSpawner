package github.nighter.smartspawner.spawner.model;

import com.google.common.util.concurrent.AtomicDouble;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Running total of what a spawner's stored loot is worth, so a sell does not have to re-price the
 * whole inventory every time. The value is kept in step incrementally as items are added and removed
 * ({@link #applyAdded}/{@link #applyRemoved}); {@link #markDirty} forces a full {@link #recalculate}
 * on the next sell when prices or loot config may have changed underneath it.
 *
 * <p>Owned by one {@link SpawnerData} and reads the spawner's live loot config, virtual inventory and
 * price manager on demand, so it always prices against the spawner's current state.</p>
 */
final class SpawnerSellValue {

    private final SpawnerData spawner;
    private final AtomicDouble accumulated = new AtomicDouble(0.0);
    private volatile boolean dirty = true;

    SpawnerSellValue(SpawnerData spawner) {
        this.spawner = spawner;
    }

    double getAccumulated() {
        return accumulated.get();
    }

    boolean isDirty() {
        return dirty;
    }

    void markDirty() {
        this.dirty = true;
    }

    /** Adds the value of freshly stored items, unless the total is dirty and awaiting a recalculation. */
    void applyAdded(Map<ItemSignature, Long> itemsAdded) {
        if (!dirty) {
            increment(itemsAdded, createPriceCache());
        }
    }

    /** Subtracts the value of removed items, unless the total is dirty and awaiting a recalculation. */
    void applyRemoved(Map<ItemSignature, Long> itemsRemoved) {
        if (!dirty) {
            decrement(itemsRemoved, createPriceCache());
        }
    }

    private void increment(Map<ItemSignature, Long> itemsAdded, Map<String, Double> priceCache) {
        if (itemsAdded == null || itemsAdded.isEmpty()) {
            return;
        }
        double addedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : itemsAdded.entrySet()) {
            double price = findItemPrice(entry.getKey(), priceCache);
            if (price > 0.0) {
                addedValue += price * entry.getValue();
            }
        }
        if (addedValue > 0.0) {
            accumulated.addAndGet(addedValue);
        }
        this.dirty = false;
    }

    private void decrement(Map<ItemSignature, Long> itemsRemoved, Map<String, Double> priceCache) {
        if (itemsRemoved == null || itemsRemoved.isEmpty()) {
            return;
        }
        double removedValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : itemsRemoved.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            double price = findItemPrice(entry.getKey(), priceCache);
            if (price > 0.0) {
                removedValue += price * entry.getValue();
            }
        }
        subtract(removedValue);
    }

    /** Recomputes the total from the current inventory. Called on load and whenever the total is dirty. */
    void recalculate() {
        if (spawner.getLootConfig() == null) {
            accumulated.set(0.0);
            this.dirty = false;
            return;
        }

        Map<String, Double> priceCache = createPriceCache();
        double totalValue = 0.0;
        for (Map.Entry<ItemSignature, Long> entry : spawner.getVirtualInventory().getConsolidatedItems().entrySet()) {
            double price = findItemPrice(entry.getKey(), priceCache);
            if (price > 0.0) {
                totalValue += price * entry.getValue();
            }
        }
        accumulated.set(totalValue);
        this.dirty = false;
    }

    /**
     * Maps item key to unit price for the spawner's loot. Prefers the live price from
     * {@link ItemPriceManager} over {@link LootItem#sellPrice()}, which can be stale when a shop
     * plugin had not published prices yet at config-load time.
     */
    Map<String, Double> createPriceCache() {
        Map<String, Double> cache = new HashMap<>();
        if (spawner.getLootConfig() == null) {
            return cache;
        }

        ItemPriceManager priceManager = spawner.getPlugin().getItemPriceManager();
        for (LootItem lootItem : spawner.getLootConfig().getAllItems()) {
            double price = (priceManager != null) ? priceManager.getPrice(lootItem.material()) : 0.0;
            if (price <= 0.0) {
                price = lootItem.sellPrice();
            }
            if (price > 0.0) {
                ItemStack template = lootItem.createItemStack();
                if (template != null) {
                    cache.put(createItemKey(new ItemSignature(template)), price);
                }
            }
        }
        return cache;
    }

    private void subtract(double removedValue) {
        if (removedValue <= 0.0) {
            return;
        }
        double current;
        double updated;
        do {
            current = accumulated.get();
            updated = Math.max(0.0, current - removedValue);
        } while (!accumulated.compareAndSet(current, updated));
    }

    private double findItemPrice(ItemSignature signature, Map<String, Double> priceCache) {
        if (priceCache == null) {
            return 0.0;
        }
        Double price = priceCache.get(createItemKey(signature));
        return price != null ? price : 0.0;
    }

    /**
     * Builds the price-cache key for an item from its material, enchantments and display name. Must
     * stay in step with {@code SpawnerSellManager}'s key, which prices the same items on the sell side.
     */
    private String createItemKey(ItemSignature signature) {
        StringBuilder key = new StringBuilder(signature.getMaterial().name());
        ItemMeta meta = signature.getUnsafeTemplateRef().getItemMeta(); // read-only

        if (signature.hasItemMeta() && meta.hasEnchants()) {
            key.append("_enchants:");
            meta.getEnchants().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(e -> e.getKey().toString())))
                    .forEach(e -> key.append(e.getKey().getKey()).append(":").append(e.getValue()).append(","));
        }
        if (signature.hasItemMeta() && meta.hasDisplayName()) {
            key.append("_name:").append(meta.displayName());
        }
        return key.toString();
    }
}
