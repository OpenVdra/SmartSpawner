package github.nighter.smartspawner.spawner.lootgen.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One row of a loot table: an item template plus the distribution that decides when and how much of
 * it drops.
 *
 * <p>The template is built once while the config is parsed, by
 * {@link github.nighter.smartspawner.spawner.config.ConfiguredItemParser}, so it already carries
 * every component the entry asked for. Nothing here inspects the item to decide what to build, which
 * is why supporting a new item property takes no change to this class.</p>
 *
 * @param template      the item this row drops, amount 1, or null when the entry could not be resolved
 * @param material      the template's material, kept for price lookups and GUI display names
 * @param configuredItem the entry's item: value, kept so a price can be looked up under it
 * @param minDurability start of a random damage range, null when the entry has no range
 * @param maxDurability end of a random damage range, null when the entry has no range
 */
public record LootItem(ItemStack template, Material material, String configuredItem,
                       int minAmount, int maxAmount, double chance,
                       Integer minDurability, Integer maxDurability, double sellPrice) {

    public ItemStack createItemStack() {
        if (template == null) {
            return null; // Item not available in this version
        }

        ItemStack item = template.clone();

        // A single fixed damage value is already baked into the template, so only a range is rolled here.
        if (minDurability != null && maxDurability != null
                && item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(ThreadLocalRandom.current().nextInt(maxDurability - minDurability + 1) + minDurability);
            item.setItemMeta(damageable);
        }

        return item;
    }

    public int generateAmount(Random random) {
        return random.nextInt(maxAmount - minAmount + 1) + minAmount;
    }

    public double getAverageAmount() {
        return (this.maxAmount + this.minAmount) / 2.0;
    }

    public boolean isAvailable() {
        return template != null;
    }
}
