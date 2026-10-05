package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;

import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves a typed spawner name against both {@code spawner_mobs.yml} and {@code spawner_items.yml},
 * so commands can take {@code zombie} or {@code diamond} without asking which file it is in.
 *
 * <p>Each file resolves its own names, aliases and 1.8 {@code <type>_spawner} names. When both files
 * know a name, the mob spawner wins, and {@link #warnConflicts} reports it once per load.</p>
 */
public final class SpawnerNames {
    /** Words {@code /ss give} reads as keywords before any spawner name. */
    public static final Set<String> RESERVED = Set.of("vanilla", "smart_spawner", "item_spawner", "vanilla_spawner");

    private SpawnerNames() {}

    /** A resolved name: exactly one of {@code mob} and {@code item} is set. */
    public record Resolved(SpawnerSettingsConfig.MobDefinition mob, ItemSpawnerSettingsConfig.ItemDefinition item) {}

    public static Resolved resolve(SmartSpawner plugin, String input) {
        var mob = plugin.getSpawnerSettingsConfig().resolve(input);
        if (mob != null) return new Resolved(mob, null);
        var item = plugin.getItemSpawnerSettingsConfig().resolve(input);
        return item != null ? new Resolved(null, item) : null;
    }

    /** Every spawner name across both files, for tab completion. */
    public static Set<String> suggestionNames(SmartSpawner plugin) {
        Set<String> names = new TreeSet<>(plugin.getSpawnerSettingsConfig().getDefinitionNames());
        names.addAll(plugin.getItemSpawnerSettingsConfig().getDefinitionNames());
        return names;
    }

    /** Logs every name that cannot reach its own spawner. Call after both files are loaded. */
    public static void warnConflicts(SmartSpawner plugin) {
        Set<String> mobNames = plugin.getSpawnerSettingsConfig().getDefinitionNames();
        Set<String> itemNames = plugin.getItemSpawnerSettingsConfig().getDefinitionNames();
        for (String name : itemNames) {
            if (mobNames.contains(name)) {
                plugin.getLogger().warning("Spawner name '" + name + "' is in both spawner_mobs.yml and spawner_items.yml."
                        + " Commands use the mob spawner. Rename one of them to keep them apart.");
            }
        }
        for (String name : RESERVED) {
            if (mobNames.contains(name) || itemNames.contains(name)) {
                plugin.getLogger().warning("Spawner name '" + name + "' is a reserved /ss give keyword and cannot be"
                        + " given by name. Rename it.");
            }
        }
    }
}
