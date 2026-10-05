package github.nighter.smartspawner.commands.editloot;

import lombok.Getter;

/**
 * The two settings files whose loot tables {@code /ss editloot} can change.
 *
 * <p>They share a shape: a section per spawner, top level or under {@code custom_spawners}, each with
 * a {@code loot} section of labelled rows. They differ only in which file holds them and which live
 * component rereads them.</p>
 */
@Getter
public enum LootEditorTarget {

    /** {@code spawner_mobs.yml}, the mob spawner loot tables. */
    SMART_SPAWNER("spawner_mobs.yml", "editloot.mob_loot_title"),

    /** {@code spawner_items.yml}, the item spawner loot tables. */
    ITEM_SPAWNER("spawner_items.yml", "editloot.item_loot_title");

    private final String fileName;
    private final String titleKey;

    LootEditorTarget(String fileName, String titleKey) {
        this.fileName = fileName;
        this.titleKey = titleKey;
    }
}
