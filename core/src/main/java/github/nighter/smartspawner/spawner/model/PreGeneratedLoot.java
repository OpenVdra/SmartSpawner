package github.nighter.smartspawner.spawner.model;

import java.util.Map;

/**
 * The next batch of loot, generated ahead of time so the GUI can show it instantly when the timer
 * fires. All access is synchronized on this holder, which is the mutual exclusion the pre-generated
 * fields require.
 */
final class PreGeneratedLoot {

    private Map<ItemSignature, Long> items;
    private long experience;
    private boolean generating;

    synchronized void store(Map<ItemSignature, Long> items, long experience) {
        this.items = items;
        this.experience = experience;
    }

    synchronized Map<ItemSignature, Long> takeItems() {
        Map<ItemSignature, Long> taken = items;
        items = null;
        return taken;
    }

    synchronized long takeExperience() {
        long taken = experience;
        experience = 0;
        return taken;
    }

    synchronized boolean has() {
        return (items != null && !items.isEmpty()) || experience > 0;
    }

    synchronized void setGenerating(boolean generating) {
        this.generating = generating;
    }

    synchronized boolean isGenerating() {
        return generating;
    }

    synchronized void clear() {
        items = null;
        experience = 0;
        generating = false;
    }
}
