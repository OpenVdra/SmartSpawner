package github.nighter.smartspawner.commands.editloot;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.items.CustomItemRegistry;
import github.nighter.smartspawner.spawner.config.ConfiguredItemParser;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Every read and write {@code /ss editloot} performs against the two loot files.
 *
 * <p>All mutations follow the same shape: load the file from disk, apply one change, save, reload the
 * live components that read it. Reloading from disk on each write rather than holding a parsed copy
 * means an admin editing the file by hand while a GUI is open loses at most the single field being
 * changed, instead of having their whole file overwritten from a stale snapshot. GUI navigation, by
 * contrast, reads the in-memory snapshot and never touches disk.</p>
 *
 * <p>{@link YamlConfiguration} carries comments through a load and save, so the documentation inside
 * the shipped files survives being written by the editor.</p>
 */
public class LootEditorService {

    private final SmartSpawner plugin;
    private final Map<LootEditorTarget, YamlConfiguration> snapshots =
            new EnumMap<>(LootEditorTarget.class);

    public LootEditorService(SmartSpawner plugin) {
        this.plugin = plugin;
        reload();
    }

    // ============== Snapshots ==============

    private File fileOf(LootEditorTarget target) {
        return new File(plugin.getDataFolder(), target.getFileName());
    }

    /** Refreshes the editor's in-memory snapshots. Called on construction and on a full reload. */
    public synchronized void reload() {
        for (LootEditorTarget target : LootEditorTarget.values()) {
            snapshots.put(target, YamlConfiguration.loadConfiguration(fileOf(target)));
        }
    }

    private synchronized YamlConfiguration snapshot(LootEditorTarget target) {
        return snapshots.get(target);
    }

    // ============== Entry lookup ==============

    /** Points a typed spawner name at the file that holds it, or null when nothing matches. */
    public synchronized EntryRef findEntry(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim();
        // Mobs first, then items: a name is expected to be unique across the two in practice.
        for (LootEditorTarget target : LootEditorTarget.values()) {
            if (snapshot(target).isConfigurationSection(key)) {
                return new EntryRef(target, key);
            }
        }
        return null;
    }

    /** Every spawner name across both files, sorted, for tab completion. */
    public synchronized List<String> listAllEntryNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LootEditorTarget target : LootEditorTarget.values()) {
            YamlConfiguration config = snapshot(target);
            for (String key : config.getKeys(false)) {
                if (config.isConfigurationSection(key)) {
                    names.add(key);
                }
            }
        }
        return new ArrayList<>(names);
    }

    public synchronized boolean hasEntry(LootEditorTarget target, String key) {
        return snapshot(target).isConfigurationSection(key);
    }

    // ============== Reading loot ==============

    /** Loot row labels for one entry, in file order. */
    public synchronized List<String> listLootKeys(LootEditorTarget target, String key) {
        ConfigurationSection loot = snapshot(target).getConfigurationSection(key + ".loot");
        return loot == null ? List.of() : new ArrayList<>(loot.getKeys(false));
    }

    public synchronized LootView readLoot(LootEditorTarget target, String key, String lootKey) {
        ConfigurationSection section = snapshot(target).getConfigurationSection(key + ".loot." + lootKey);
        return readLootSection(lootKey, section);
    }

    /** Reads a whole loot screen from one YAML snapshot. */
    public synchronized List<LootView> readLootList(LootEditorTarget target, String key) {
        ConfigurationSection loot = snapshot(target).getConfigurationSection(key + ".loot");
        if (loot == null) {
            return List.of();
        }
        List<LootView> result = new ArrayList<>();
        for (String lootKey : loot.getKeys(false)) {
            LootView view = readLootSection(lootKey, loot.getConfigurationSection(lootKey));
            if (view != null) {
                result.add(view);
            }
        }
        return result;
    }

    private LootView readLootSection(String lootKey, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String rawItem = section.getString("item");
        ItemStack preview;
        try {
            preview = rawItem == null ? null : ConfiguredItemParser.parse(rawItem);
        } catch (IllegalArgumentException e) {
            preview = null;
        }
        if (rawItem == null) {
            rawItem = "";
        }

        int[] amount = parseRange(section.getString("amount", "1-1"), 1, 1);
        int[] durability = section.contains("durability")
                ? parseRange(section.getString("durability"), 0, 0)
                : null;

        return new LootView(lootKey, rawItem, preview,
                amount[0], amount[1], section.getDouble("chance", 100.0),
                durability == null ? null : durability[0],
                durability == null ? null : durability[1]);
    }

    // ============== Writing loot ==============

    public void setLootAmount(LootEditorTarget target, String key, String lootKey, int min, int max) {
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".amount",
                low == high ? String.valueOf(low) : low + "-" + high));
    }

    public void setLootChance(LootEditorTarget target, String key, String lootKey, double chance) {
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".chance",
                round(clamp(chance, 0.0, 100.0))));
    }

    /** @param min null on either side clears the durability range entirely. */
    public void setLootDurability(LootEditorTarget target, String key, String lootKey, Integer min, Integer max) {
        mutate(target, config -> {
            String path = key + ".loot." + lootKey + ".durability";
            if (min == null || max == null) {
                config.set(path, null);
                return;
            }
            int low = Math.max(0, Math.min(min, max));
            int high = Math.max(low, Math.max(min, max));
            config.set(path, low == high ? String.valueOf(low) : low + "-" + high);
        });
    }

    /**
     * Points a loot row at an item the admin dropped into the capture GUI.
     *
     * <p>A plain vanilla item is written as its material name so the file stays readable, and an item
     * another plugin owns is written as that plugin's reference. Anything else is written as a
     * {@code nbt:} blob, the only form that survives a round trip without losing components.</p>
     */
    public void setLootItem(LootEditorTarget target, String key, String lootKey, ItemStack item) {
        String value = describeItem(item);
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".item", value));
    }

    private String describeItem(ItemStack item) {
        if (describesItselfFully(item)) {
            return item.getType().name();
        }
        String custom = CustomItemRegistry.describe(item);
        return custom != null ? custom : ConfiguredItemParser.toNbtValue(item);
    }

    /** Adds a loot row for a dropped item under a generated, unused numeric label. */
    public String addLoot(LootEditorTarget target, String key, ItemStack item, int min, int max,
                          double chance, Integer durabilityMin, Integer durabilityMax) {
        String label = uniqueLootLabel(target, key);
        String value = describeItem(item);
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));

        mutate(target, config -> {
            String path = key + ".loot." + label;
            config.set(path + ".item", value);
            config.set(path + ".amount", low == high ? String.valueOf(low) : low + "-" + high);
            config.set(path + ".chance", round(clamp(chance, 0.0, 100.0)));
            if (durabilityMin != null && durabilityMax != null) {
                int dLow = Math.max(0, Math.min(durabilityMin, durabilityMax));
                int dHigh = Math.max(dLow, Math.max(durabilityMin, durabilityMax));
                config.set(path + ".durability", dLow == dHigh ? String.valueOf(dLow) : dLow + "-" + dHigh);
            }
        });
        return label;
    }

    public void removeLoot(LootEditorTarget target, String key, String lootKey) {
        mutate(target, config -> config.set(key + ".loot." + lootKey, null));
    }

    // ============== Internals ==============

    private interface Mutation {
        void apply(YamlConfiguration config);
    }

    /** Load, change, save, reload. The one place these files are written. */
    private synchronized void mutate(LootEditorTarget target, Mutation mutation) {
        File file = fileOf(target);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mutation.apply(config);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + target.getFileName() + ": " + e.getMessage());
            return;
        }

        snapshots.put(target, config);
        applyReload(target);
    }

    /**
     * Rereads the file into the live plugin. Spawner settings feed the loot tables held by every
     * loaded spawner, so those are recalculated too, and the spawner item shows its loot in the lore,
     * so its caches go as well.
     */
    private void applyReload(LootEditorTarget target) {
        if (target == LootEditorTarget.SMART_SPAWNER) {
            plugin.getSpawnerSettingsConfig().reload();
        } else {
            plugin.getItemSpawnerSettingsConfig().reload();
        }
        plugin.getSpawnerManager().reloadSpawnerDropsAndConfigs();
        if (plugin.getSpawnerItemFactory() != null) {
            plugin.getSpawnerItemFactory().clearAllCaches();
        }
    }

    /**
     * True when the material name alone rebuilds this item, so the config can stay readable instead of
     * holding an opaque blob.
     */
    private boolean describesItselfFully(ItemStack item) {
        return !item.hasItemMeta() || item.getItemMeta() == null || item.getItemMeta().equals(
                new ItemStack(item.getType()).getItemMeta());
    }

    /**
     * Loot rows are labelled by position, so a new one takes the lowest free number rather than being
     * named after its item. A label carries no meaning: the {@code item:} line is what says what
     * drops, which is why several rows can hold variants of the same material.
     */
    private String uniqueLootLabel(LootEditorTarget target, String key) {
        List<String> taken = listLootKeys(target, key);
        for (int i = 1; i <= taken.size() + 1; i++) {
            String candidate = String.valueOf(i);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return String.valueOf(taken.size() + 1);
    }

    /** Splits an {@code "a-b"} or {@code "a"} range, falling back on unparseable input. */
    public static int[] parseRange(String raw, int fallbackMin, int fallbackMax) {
        String value = raw == null ? "" : raw.trim();
        try {
            int separator = value.indexOf('-', 1);
            if (separator < 0) {
                int single = Integer.parseInt(value);
                return new int[]{single, single};
            }
            return new int[]{
                    Integer.parseInt(value.substring(0, separator).trim()),
                    Integer.parseInt(value.substring(separator + 1).trim())
            };
        } catch (NumberFormatException e) {
            return new int[]{fallbackMin, fallbackMax};
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Keeps percentages to one decimal so the file does not fill up with float noise. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Resolves a spawner name to the file that holds it. */
    @Getter
    public static final class EntryRef {
        private final LootEditorTarget target;
        private final String entryKey;

        public EntryRef(LootEditorTarget target, String entryKey) {
            this.target = target;
            this.entryKey = entryKey;
        }
    }

    /** A loot row as the GUI needs to show it. */
    public record LootView(String key, String rawItem, ItemStack preview,
                           int minAmount, int maxAmount, double chance,
                           Integer minDurability, Integer maxDurability) {

        public boolean isBroken() {
            return preview == null;
        }

        public String amountLabel() {
            return minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + "-" + maxAmount;
        }

        public String durabilityLabel() {
            if (minDurability == null || maxDurability == null) {
                return null;
            }
            return minDurability.equals(maxDurability)
                    ? String.valueOf(minDurability)
                    : minDurability + "-" + maxDurability;
        }
    }
}
