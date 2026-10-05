package github.nighter.smartspawner.spawner.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Logger;

/** The icon a spawner shows in menus: an item, or a player head with a texture hash. */
public record SpawnerHead(Material material, String texture) {

    public boolean hasTexture() {
        return material == Material.PLAYER_HEAD && texture != null && !texture.isBlank()
                && !texture.equalsIgnoreCase("null");
    }

    /**
     * Reads a {@code mob_head} section, or returns null when the entry has none, so the caller can
     * fall back to the base spawner's head.
     */
    public static SpawnerHead parse(ConfigurationSection entry, Material fallback, String label, Logger logger) {
        ConfigurationSection section = entry.getConfigurationSection("mob_head");
        if (section == null) {
            return null;
        }
        String materialName = section.getString("item", fallback.name());
        Material material = Material.matchMaterial(materialName == null ? "" : materialName.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            logger.warning("Invalid mob_head item '" + materialName + "' for " + label + ", using " + fallback.name());
            material = fallback;
        }
        return new SpawnerHead(material, section.getString("hash_texture"));
    }
}
