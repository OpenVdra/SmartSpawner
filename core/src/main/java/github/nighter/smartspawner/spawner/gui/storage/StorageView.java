package github.nighter.smartspawner.spawner.gui.storage;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

/**
 * Per-open-inventory render cache. Holds the last {@link ItemStack} painted into each slot plus the
 * {@code storageVersion} that image reflects, so {@link StorageRenderer} can patch only the slots
 * that actually changed instead of clearing and rewriting the whole page on every action.
 *
 * <p>Confined to the region thread that renders the owning inventory; not thread-safe by itself.
 * A single {@link StoragePageHolder} owns one of these for the lifetime of its open inventory.
 */
public class StorageView {
    private ItemStack[] rendered = new ItemStack[0];

    /** The storage version this cached image reflects; {@code -1} until first render. */
    @Getter @Setter
    private long renderedVersion = -1;

    /** Reallocates (and clears) the cache when the inventory size changes, e.g. a page resize. */
    void ensureSize(int size) {
        if (rendered.length != size) {
            rendered = new ItemStack[size];
        }
    }

    /**
     * The last {@link ItemStack} painted into {@code slot}, or {@code null} if empty/out of range.
     * Public so the reconciler in the {@code action} subpackage can diff against the painted image;
     * {@link #set} and {@link #ensureSize} stay package-private to {@link StorageRenderer}.
     */
    public ItemStack get(int slot) {
        return (slot >= 0 && slot < rendered.length) ? rendered[slot] : null;
    }

    void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < rendered.length) {
            rendered[slot] = item;
        }
    }
}
