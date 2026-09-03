package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.hooks.items.CustomItemRegistry;
import github.nighter.smartspawner.hooks.items.providers.CustomItemProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Turns the {@code item:} value of a loot entry into an {@link ItemStack}.
 *
 * <p>One field accepts four forms so simple entries stay readable while custom items are still
 * expressible. They are told apart in this order, and the order matters: checking the plugin
 * reference form before the material form would read {@code minecraft:arrow} as an item belonging
 * to a plugin named "minecraft".</p>
 *
 * <table>
 *   <tr><th>Order</th><th>Condition</th><th>Form</th><th>Example</th></tr>
 *   <tr><td>1</td><td>starts with {@code nbt:}</td><td>Base64 of raw NBT</td><td>{@code nbt:H4sIAAAA...}</td></tr>
 *   <tr><td>2</td><td>contains {@code [}</td><td>vanilla component syntax</td><td>{@code tipped_arrow[potion_contents={potion:"minecraft:poison"}]}</td></tr>
 *   <tr><td>3</td><td>matches a material</td><td>plain material</td><td>{@code ARROW}</td></tr>
 *   <tr><td>4</td><td>contains {@code :}</td><td>item owned by another plugin</td><td>{@code mmoitems:SWORD:CUTLASS}</td></tr>
 * </table>
 *
 * <p>Form 2 is the string the vanilla {@code /give} command autocompletes, so server owners can
 * build an item in game and copy it straight into the config. Form 1 is what an in-game editor
 * would write, since it round-trips any item losslessly. Form 4 is handed to the
 * {@link CustomItemProvider} claiming the part before the first colon; everything after it is that
 * plugin's own syntax, so an MMOItems value carries two colons. A namespace no installed plugin
 * claims is rejected with a clear message rather than silently treated as an unknown material.</p>
 *
 * <p>{@code nbt} is a reserved namespace: no plugin reference may use it.</p>
 */
public final class ConfiguredItemParser {

    private static final String NBT_PREFIX = "nbt:";

    private ConfiguredItemParser() {
    }

    /**
     * Resolve a configured item value.
     *
     * @param raw the {@code item:} value, or the loot section key for the legacy material-as-key form
     * @return an item stack of amount 1, never null
     * @throws IllegalArgumentException when the value cannot be resolved, with a message meant to be
     *         shown to the server owner
     */
    public static ItemStack parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("item is empty");
        }

        String value = raw.trim();

        if (value.regionMatches(true, 0, NBT_PREFIX, 0, NBT_PREFIX.length())) {
            return fromNbt(value.substring(NBT_PREFIX.length()));
        }

        if (value.indexOf('[') >= 0) {
            return fromComponents(value);
        }

        Material material = Material.matchMaterial(value);
        if (material != null) {
            if (!material.isItem()) {
                throw new IllegalArgumentException("'" + value + "' is a block, not an item");
            }
            return new ItemStack(material, 1);
        }

        int separator = value.indexOf(':');
        if (separator >= 0) {
            return fromProvider(value.substring(0, separator), value.substring(separator + 1), value);
        }

        throw new IllegalArgumentException("'" + value + "' is not a material available on this server version");
    }

    private static ItemStack fromNbt(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nbt value is not valid Base64", e);
        }

        try {
            ItemStack item = ItemStack.deserializeBytes(bytes);
            if (item == null || item.getType() == Material.AIR) {
                throw new IllegalArgumentException("nbt value decoded to an empty item");
            }
            return item.asQuantity(1);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("nbt value could not be read as an item: " + e.getMessage(), e);
        }
    }

    private static ItemStack fromComponents(String value) {
        try {
            ItemStack item = Bukkit.getItemFactory().createItemStack(value);
            return item.asQuantity(1);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid item syntax: " + e.getMessage(), e);
        }
    }

    private static ItemStack fromProvider(String namespace, String id, String value) {
        CustomItemProvider provider = CustomItemRegistry.find(namespace);
        if (provider == null) {
            throw new IllegalArgumentException("'" + value + "' looks like an item from another plugin, "
                    + "but no installed plugin claims '" + namespace + "'. Use the item's material name, "
                    + "the /give syntax, or an nbt: value instead");
        }

        ItemStack item;
        try {
            item = provider.createItem(id);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + value + "' could not be read by "
                    + provider.getPluginName() + ": " + e.getMessage(), e);
        }

        if (item == null || item.getType() == Material.AIR) {
            throw new IllegalArgumentException("'" + value + "' resolved to an empty item");
        }
        return item.asQuantity(1);
    }

    /**
     * Encode an item into the {@code nbt:} form. This is what an in-game editor writes back into the
     * config, and it round-trips every item component.
     */
    public static String toNbtValue(ItemStack item) {
        return NBT_PREFIX + Base64.getEncoder().encodeToString(item.asQuantity(1).serializeAsBytes());
    }
}
