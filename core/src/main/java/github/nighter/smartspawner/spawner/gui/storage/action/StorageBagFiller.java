package github.nighter.smartspawner.spawner.gui.storage.action;

import github.nighter.smartspawner.spawner.model.ItemSignature;
import github.nighter.smartspawner.spawner.model.VirtualInventory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure player-inventory and item-projection math for the storage bulk actions (take-all, drop-page).
 * Every method is a static function of its inputs with no plugin state, so it is trivial to reason
 * about and safe to call from any region thread. It never touches the {@link VirtualInventory}; the
 * caller owns removal from storage.
 */
final class StorageBagFiller {

    private StorageBagFiller() {
    }

    /**
     * Places {@code amount} of {@code template} into the player's main inventory, stacking into
     * matching partial stacks first, then empty slots. The caller (take-all / drop) must have verified
     * the amount fits (e.g. via {@link #simulateBagFill}), so nothing is dropped.
     */
    static void addToPlayerInventory(PlayerInventory playerInv, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot != null && slot.getType() != Material.AIR && slot.isSimilar(template)) {
                int room = maxStack - slot.getAmount();
                if (room > 0) {
                    int add = Math.min(room, remaining);
                    slot.setAmount(slot.getAmount() + add);
                    remaining -= add;
                }
            }
        }

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int add = Math.min(remaining, maxStack);
                ItemStack newStack = template.clone();
                newStack.setAmount(add);
                playerInv.setItem(i, newStack);
                remaining -= add;
            }
        }
    }

    /**
     * Simulates filling the player's bag with the given signatures (in iteration order) without
     * mutating the real inventory. Returns how much of each signature would fit, competing for
     * the same empty slots as a real fill would. Used to size a take-all before committing it.
     */
    static Map<ItemSignature, Long> simulateBagFill(PlayerInventory playerInv,
                                                    Collection<Map.Entry<ItemSignature, Long>> ordered) {
        ItemStack[] slots = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = playerInv.getItem(i);
            slots[i] = (s == null || s.getType() == Material.AIR) ? null : s.clone();
        }

        Map<ItemSignature, Long> acceptable = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : ordered) {
            ItemSignature signature = entry.getKey();
            long avail = entry.getValue() == null ? 0L : entry.getValue();
            if (signature == null || avail <= 0) {
                continue;
            }

            ItemStack template = signature.getTemplate();
            int maxStack = template.getMaxStackSize();
            long placed = 0;

            for (int i = 0; i < 36 && placed < avail; i++) {
                ItemStack slot = slots[i];
                if (slot != null && slot.isSimilar(template)) {
                    int room = maxStack - slot.getAmount();
                    if (room > 0) {
                        int add = (int) Math.min(room, avail - placed);
                        slot.setAmount(slot.getAmount() + add);
                        placed += add;
                    }
                }
            }
            for (int i = 0; i < 36 && placed < avail; i++) {
                if (slots[i] == null) {
                    int add = (int) Math.min(maxStack, avail - placed);
                    ItemStack ns = template.clone();
                    ns.setAmount(add);
                    slots[i] = ns;
                    placed += add;
                }
            }

            if (placed > 0) {
                acceptable.put(signature, placed);
            }
        }
        return acceptable;
    }

    /** Splits a signature-to-amount map into displayable stacks keyed by sequential slot index. */
    static Map<Integer, ItemStack> projectToSlots(Map<ItemSignature, Long> items) {
        Map<Integer, ItemStack> out = new HashMap<>();
        int slot = 0;
        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                out.put(slot++, stack);
                remaining -= amt;
            }
        }
        return out;
    }

    /** Consolidates a slot-keyed ItemStack map back into signature-to-amount. */
    static Map<ItemSignature, Long> consolidateSlots(Map<Integer, ItemStack> slots) {
        Map<ItemSignature, Long> out = new HashMap<>();
        for (ItemStack item : slots.values()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            out.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        return out;
    }
}
