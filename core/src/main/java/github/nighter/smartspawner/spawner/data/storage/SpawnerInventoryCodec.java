package github.nighter.smartspawner.spawner.data.storage;

import github.nighter.smartspawner.spawner.model.ItemSignature;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary codec for a spawner's virtual inventory.
 *
 * <p>The virtual inventory is a map of distinct item templates to a {@code long} count, and that
 * count routinely exceeds {@link ItemStack#getMaxStackSize()}, so items and amounts have to be
 * stored separately. Item templates go through Paper's raw NBT serialization
 * ({@link ItemStack#serializeItemsAsBytes(java.util.Collection)}), which keeps every item component
 * (enchantments, display name, lore, custom model data, persistent data) instead of the
 * material-and-damage summary the legacy string format kept.</p>
 *
 * <h2>Grouping by base item (format v2)</h2>
 *
 * <p>Damageable loot with a durability <em>range</em> rolls a fresh damage value per drop
 * ({@code LootItem.createItemStack}), so a single loot entry fragments into many distinct
 * {@link ItemSignature}s that differ only in their damage. Serializing each one in full repeats the
 * whole base NBT (name, lore, enchantments) once per damage value. v2 instead groups entries that
 * share the same base item &mdash; every component except the damage value &mdash; and serializes
 * that base NBT once, followed by the compact {@code (damage, amount)} table for the group. The
 * in-memory model is unchanged; this is purely a storage layout.</p>
 *
 * <p>Grouping is conservative: an item is only normalized (its damage stripped to build the group
 * key) when it actually carries damage. Undamaged items and non-damageable items are never touched,
 * so for the overwhelming common case the blob is byte-for-byte what v1 produced apart from the
 * extra per-group counters, and decode reproduces the exact original {@link ItemStack}.</p>
 *
 * <p>Layout of the produced blob (v2):</p>
 * <pre>
 * byte   format version (2)
 * int    group count g
 * -- per group, in template order:
 *   int  variant count v
 *   (int damage, long amount) repeated v times
 * int    length of the item payload
 * byte[] ItemStack.serializeItemsAsBytes(baseTemplates), g bases each with amount 1 and damage 0
 * </pre>
 *
 * <p>The previous v1 layout was a flat {@code [int n][long amount x n][int len][payload]} with one
 * full template per entry; {@link #decode(byte[])} still reads it. Bump {@link #FORMAT_VERSION} and
 * keep a decode branch for each old value when the layout changes. An empty inventory encodes to
 * {@code null} so the column stays NULL rather than holding an empty payload.</p>
 */
public final class SpawnerInventoryCodec {

    private static final byte FORMAT_VERSION = 2;
    private static final byte FORMAT_VERSION_V1 = 1;

    private SpawnerInventoryCodec() {
    }

    /**
     * Encode a consolidated inventory snapshot.
     *
     * @param items distinct item signatures mapped to their total count
     * @return the encoded blob, or null when there is nothing to store
     */
    public static byte[] encode(Map<ItemSignature, Long> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return null;
        }

        // Group by base item (every component except the damage value) so shared NBT is stored once.
        // Insertion order is preserved so the serialized base templates line up with the group table.
        LinkedHashMap<ItemStack, LinkedHashMap<Integer, Long>> groups = new LinkedHashMap<>(Math.max(16, items.size() * 2));

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amount = entry.getValue();
            if (amount == null || amount <= 0L) {
                continue;
            }

            ItemStack template = signature.getTemplate();
            if (template == null || template.getType() == Material.AIR) {
                continue;
            }

            int damage = signature.getDamage();
            if (damage != 0) {
                ItemMeta meta = signature.hasItemMeta() ? template.getItemMeta() : null;
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(0);
                    template.setItemMeta(damageable);
                }
            }

            groups.computeIfAbsent(template, k -> new LinkedHashMap<>())
                    .merge(damage, amount, Long::sum);
        }

        if (groups.isEmpty()) {
            return null;
        }

        List<ItemStack> baseTemplates = new ArrayList<>(groups.keySet());
        byte[] itemPayload = ItemStack.serializeItemsAsBytes(baseTemplates);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(itemPayload.length + (groups.size() * 16) + 16);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(FORMAT_VERSION);
            out.writeInt(groups.size());
            for (LinkedHashMap<Integer, Long> variants : groups.values()) {
                out.writeInt(variants.size());
                for (Map.Entry<Integer, Long> variant : variants.entrySet()) {
                    out.writeInt(variant.getKey());
                    out.writeLong(variant.getValue());
                }
            }
            out.writeInt(itemPayload.length);
            out.write(itemPayload);
        }

        return buffer.toByteArray();
    }

    /**
     * Decode a blob produced by {@link #encode(Map)}, or by any earlier format version.
     *
     * @param blob the stored payload, may be null or empty
     * @return item templates mapped to their total count, never null
     */
    public static Map<ItemStack, Long> decode(byte[] blob) throws IOException {
        if (blob == null || blob.length == 0) {
            return Map.of();
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            byte version = in.readByte();
            return switch (version) {
                case FORMAT_VERSION_V1 -> decodeV1(in);
                case FORMAT_VERSION -> decodeV2(in);
                default -> throw new IOException("Unsupported spawner inventory format version: " + version);
            };
        }
    }

    /** Legacy flat layout: one full template per entry. */
    private static Map<ItemStack, Long> decodeV1(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative spawner inventory entry count: " + count);
        }
        if (count == 0) {
            return Map.of();
        }

        long[] amounts = new long[count];
        for (int i = 0; i < count; i++) {
            amounts[i] = in.readLong();
        }

        byte[] itemPayload = readPayload(in);

        ItemStack[] templates = ItemStack.deserializeItemsFromBytes(itemPayload);
        if (templates.length != count) {
            throw new IOException("Spawner inventory entry count mismatch: " + count
                    + " amounts but " + templates.length + " items");
        }

        Map<ItemStack, Long> result = new LinkedHashMap<>(Math.max(16, count * 2));
        for (int i = 0; i < count; i++) {
            ItemStack template = templates[i];
            if (template == null || template.getType() == Material.AIR || amounts[i] <= 0L) {
                continue;
            }
            result.merge(template, amounts[i], Long::sum);
        }
        return result;
    }

    /** Grouped layout: one base template per group plus a {@code (damage, amount)} table. */
    private static Map<ItemStack, Long> decodeV2(DataInputStream in) throws IOException {
        int groupCount = in.readInt();
        if (groupCount < 0) {
            throw new IOException("Negative spawner inventory group count: " + groupCount);
        }
        if (groupCount == 0) {
            return Map.of();
        }

        int[] variantCounts = new int[groupCount];
        int[][] damages = new int[groupCount][];
        long[][] amounts = new long[groupCount][];

        for (int g = 0; g < groupCount; g++) {
            int variants = in.readInt();
            if (variants < 0) {
                throw new IOException("Negative spawner inventory variant count: " + variants);
            }
            int[] groupDamages = new int[variants];
            long[] groupAmounts = new long[variants];
            for (int i = 0; i < variants; i++) {
                groupDamages[i] = in.readInt();
                groupAmounts[i] = in.readLong();
            }
            variantCounts[g] = variants;
            damages[g] = groupDamages;
            amounts[g] = groupAmounts;
        }

        byte[] itemPayload = readPayload(in);

        ItemStack[] bases = ItemStack.deserializeItemsFromBytes(itemPayload);
        if (bases.length != groupCount) {
            throw new IOException("Spawner inventory group count mismatch: " + groupCount
                    + " groups but " + bases.length + " base items");
        }

        Map<ItemStack, Long> result = new LinkedHashMap<>(Math.max(16, groupCount * 2));
        for (int g = 0; g < groupCount; g++) {
            ItemStack base = bases[g];
            if (base == null || base.getType() == Material.AIR) {
                continue;
            }
            for (int i = 0; i < variantCounts[g]; i++) {
                long amount = amounts[g][i];
                if (amount <= 0L) {
                    continue;
                }
                int damage = damages[g][i];
                ItemStack variant = base.clone();
                if (damage != 0 && variant.getItemMeta() instanceof Damageable damageable) {
                    damageable.setDamage(damage);
                    variant.setItemMeta(damageable);
                }
                result.merge(variant, amount, Long::sum);
            }
        }
        return result;
    }

    private static byte[] readPayload(DataInputStream in) throws IOException {
        int payloadLength = in.readInt();
        if (payloadLength < 0) {
            throw new IOException("Negative spawner inventory payload length: " + payloadLength);
        }

        byte[] itemPayload = in.readNBytes(payloadLength);
        if (itemPayload.length != payloadLength) {
            throw new IOException("Truncated spawner inventory payload: expected " + payloadLength
                    + " bytes, got " + itemPayload.length);
        }
        return itemPayload;
    }

    /**
     * Total item count of a consolidated inventory snapshot, saturating instead of overflowing.
     * Persisted alongside the blob so item totals can be read without decoding it.
     */
    public static long totalItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        for (Long amount : items.values()) {
            if (amount == null || amount <= 0L) {
                continue;
            }
            long sum = total + amount;
            if (sum < total) {
                return Long.MAX_VALUE;
            }
            total = sum;
        }
        return total;
    }
}
