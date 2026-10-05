package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the operator, once per name per load, that placed spawners refer to an entry that is no
 * longer in the settings file. Thousands of spawners can share a name, so this is never per spawner.
 */
public final class SpawnerNameNotices {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SpawnerNameNotices() {}

    public static void missing(SmartSpawner plugin, String name, boolean itemSpawner) {
        if (name == null || name.isBlank()) return;
        String file = itemSpawner ? "spawner_items.yml" : "spawner_mobs.yml";
        if (!WARNED.add(file + ":" + name)) return;
        plugin.getLogger().warning("Spawners saved as '" + name + "' have no entry in " + file
                + " and use their base spawner for now. Add the entry back, or list '" + name
                + "' under the aliases of the spawner it was renamed to.");
    }

    /** Called when a settings file loads, so a name still missing after a reload is reported again. */
    public static void reset() {
        WARNED.clear();
    }
}
