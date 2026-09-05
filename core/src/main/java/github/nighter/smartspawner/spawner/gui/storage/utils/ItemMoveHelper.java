package github.nighter.smartspawner.spawner.gui.storage.utils;

import github.nighter.smartspawner.spawner.model.VirtualInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.util.*;

public class ItemMoveHelper {
    public static ItemMoveResult moveItems(ItemStack source, int amountToMove,
                                           PlayerInventory targetInv, VirtualInventory virtualInv) {
        List<ItemStack> movedItems = new ArrayList<>();
        int amountMoved = 0;

        for (int i = 0; i < 36 && amountToMove > 0; i++) {
            ItemStack current = targetInv.getItem(i);

            if (current == null || current.getType().isAir()) {
                ItemStack newStack = source.clone();
                newStack.setAmount(Math.min(amountToMove, source.getMaxStackSize()));
                targetInv.setItem(i, newStack);
                movedItems.add(newStack.clone());
                amountMoved += newStack.getAmount();
                amountToMove -= newStack.getAmount();
                break;
            } else if (current.isSimilar(source)) {
                int spaceInStack = current.getMaxStackSize() - current.getAmount();
                if (spaceInStack > 0) {
                    int addAmount = Math.min(spaceInStack, amountToMove);
                    current.setAmount(current.getAmount() + addAmount);
                    ItemStack moved = source.clone();
                    moved.setAmount(addAmount);
                    movedItems.add(moved);
                    amountMoved += addAmount;
                    amountToMove -= addAmount;
                    if (amountToMove <= 0) break;
                }
            }
        }

        return new ItemMoveResult(amountMoved, movedItems);
    }
}