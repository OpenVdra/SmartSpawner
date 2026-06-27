package github.nighter.smartspawner.spawner.properties;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemSignature {
    private final ItemStack template;
    private final int hashCode;
    // Cache purposes
    @Getter private final Material material;
    @Getter private final int maxStackSize;
    @Getter private final int damage;
    @Getter @Accessors(fluent = true) private final boolean hasItemMeta;

    public ItemSignature(ItemStack item) {
        this.template = item.asQuantity(1); // Clone with new amount
        this.material = template.getType();
        this.maxStackSize = template.getMaxStackSize();

        ItemMeta meta = template.hasItemMeta() ? template.getItemMeta() : null;

        this.hasItemMeta = meta != null;
        this.damage = extractDamage(meta);
        this.hashCode = calculateHashCode(meta);
    }

    private int calculateHashCode(ItemMeta meta) {
        // Use a faster hash algorithm and cache more item properties
        int result = 31 * this.material.ordinal(); // Using ordinal() instead of name() hashing
        result = 31 * result + this.damage;

        // Only access ItemMeta when needed
        if (this.hasItemMeta) {
            // Extract only the essential meta properties that determine similarity
            result = 31 * result + (meta.hasDisplayName() ? meta.displayName().hashCode() : 0);
            result = 31 * result + (meta.hasLore() ? meta.lore().hashCode() : 0);
            result = 31 * result + (meta.hasEnchants() ? meta.getEnchants().hashCode() : 0);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemSignature that)) return false;

        // First compare cheap properties
        if (material != that.material || this.damage != that.damage) {
            return false;
        }

        if (this.hasItemMeta != that.hasItemMeta) {
            return false;
        }

        // If both have no meta, they're similar enough
        if (!this.hasItemMeta) {
            return true;
        }

        // For complex items, fall back to isSimilar but only as a last resort
        return template.isSimilar(that.template);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    // Non-cloning method for internal use
    public ItemStack getUnsafeTemplateRef() {
        return template;
    }

    public String getMaterialName() {
        return material.name();
    }

    private int extractDamage(ItemMeta meta) {
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

}
