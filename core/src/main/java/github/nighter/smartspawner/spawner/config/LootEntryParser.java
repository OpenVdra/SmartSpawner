package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.logging.Logger;

/**
 * Reads one entry of a {@code loot:} section. Shared by {@link SpawnerSettingsConfig} and
 * {@link ItemSpawnerSettingsConfig}, which describe loot the same way.
 *
 * <p>An entry gives itself a free label and names its item in an {@code item:} field, which is what
 * allows several variants of the same material under one mob:</p>
 *
 * <pre>
 * loot:
 *   poison_arrow:
 *     item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
 *     amount: 0-1
 *     chance: 50.0
 * </pre>
 *
 * <p>{@code item:} is required. An entry without one is skipped and reported, rather than falling
 * back to reading the label as a material name: that fallback existed only for the files this
 * release replaced, and keeping it would silently accept a typo'd label as an item.</p>
 *
 * <p>A broken entry never aborts the rest of the file.</p>
 */
final class LootEntryParser {

    private LootEntryParser() {
    }

    /**
     * @param section the entry's own section
     * @param key     the entry's label, used only for reporting
     * @param context human-readable owner of this entry, used in log messages
     * @return the parsed row, or null when the entry could not be read
     */
    static LootItem parse(ConfigurationSection section, String key, ItemPriceManager priceManager,
                          Logger logger, String context) {
        String configuredItem = section.getString("item");
        if (configuredItem == null || configuredItem.isBlank()) {
            logger.warning("Skipping loot entry '" + key + "' for " + context
                    + ": it has no 'item:' line naming what should drop.");
            return null;
        }
        configuredItem = configuredItem.trim();

        ItemStack template;
        try {
            template = ConfiguredItemParser.parse(configuredItem);
        } catch (IllegalArgumentException e) {
            logger.warning("Skipping loot entry '" + key + "' for " + context + ": " + e.getMessage());
            return null;
        }

        try {
            int[] amounts = parseRange(section.getString("amount", "1-1"));
            int minAmount = Math.max(0, amounts[0]);
            int maxAmount = Math.max(minAmount, amounts[1]);

            double chance = section.getDouble("chance", 100.0);
            if (chance < 0.0 || chance > 100.0) {
                logger.warning("Loot entry '" + key + "' for " + context
                        + " has chance " + chance + ", which is outside 0 to 100. Using 100.");
                chance = 100.0;
            }

            Integer minDurability = null;
            Integer maxDurability = null;
            if (section.contains("durability")) {
                int[] durability = parseRange(section.getString("durability"));
                if (durability[0] == durability[1]) {
                    // A single value never changes, so bake it into the template and leave the
                    // per-drop path with nothing to roll.
                    applyDamage(template, durability[0]);
                } else {
                    minDurability = Math.min(durability[0], durability[1]);
                    maxDurability = Math.max(durability[0], durability[1]);
                }
            }

            Material material = template.getType();
            double sellPrice = priceManager != null
                    ? priceManager.getPrice(configuredItem, material) : 0.0;

            return new LootItem(template, material, configuredItem, minAmount, maxAmount, chance,
                    minDurability, maxDurability, sellPrice);

        } catch (NumberFormatException e) {
            logger.warning("Skipping loot entry '" + key + "' for " + context
                    + ": amount and durability must be a number or a 'min-max' range");
            return null;
        }
    }

    /** Parses {@code "3"} as 3 to 3 and {@code "1-384"} as 1 to 384. */
    private static int[] parseRange(String raw) {
        String value = raw == null ? "" : raw.trim();
        int separator = value.indexOf('-', 1);

        if (separator < 0) {
            int single = Integer.parseInt(value);
            return new int[]{single, single};
        }

        return new int[]{
                Integer.parseInt(value.substring(0, separator).trim()),
                Integer.parseInt(value.substring(separator + 1).trim())
        };
    }

    private static void applyDamage(ItemStack item, int damage) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(damageable);
        }
    }
}
