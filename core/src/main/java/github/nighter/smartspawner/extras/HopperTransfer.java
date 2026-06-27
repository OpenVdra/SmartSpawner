package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.utils.BlockPos;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;


public class HopperTransfer {

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerGuiViewManager guiManager;

    public HopperTransfer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.guiManager = plugin.getSpawnerGuiViewManager();
    }

    public void process(BlockPos hopperPos) {

        Location hopperLoc = hopperPos.toLocation();
        if (hopperLoc == null) return; // This should never happen, world was unloaded but not yet removed from the Tracker

        Block hopperBlock = hopperLoc.getBlock();

        if (hopperBlock.getType() != Material.HOPPER) return;

        Block spawnerBlock = hopperBlock.getRelative(BlockFace.UP);
        if (spawnerBlock.getType() != Material.SPAWNER) return;

        transferItems(hopperLoc, spawnerBlock.getLocation());
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        ReentrantLock lock = spawner.getInventoryLock();
        if (!lock.tryLock()) return;

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv == null) return;

            var state = hopperLoc.getBlock().getState(false);
            if (!(state instanceof Hopper hopper)) return;

            Inventory hopperInv = hopper.getInventory();

            int transferred = 0;
            int rangeStart = 0;
            int rangeSize = Math.max(plugin.getHopperConfig().getStackPerTransfer(), 9);
            List<ItemStack> removed = new ArrayList<>();

            while (transferred < plugin.getHopperConfig().getStackPerTransfer()) {
                Int2ObjectMap<ItemStack> displayItems = virtualInv.getDisplayRange(rangeStart, rangeSize);
                if (displayItems.isEmpty()) {
                    break;
                }

                for (ItemStack item : displayItems.values()) {
                    if (transferred >= plugin.getHopperConfig().getStackPerTransfer()) {
                        break;
                    }
                    if (item == null || item.getType() == Material.AIR) {
                        continue;
                    }

                    ItemStack clone = item.clone();
                    int originalAmount = clone.getAmount();

                    HashMap<Integer, ItemStack> leftovers = hopperInv.addItem(clone);

                    int insertedAmount = originalAmount;

                    if (!leftovers.isEmpty()) {
                        insertedAmount -= leftovers.values().iterator().next().getAmount();
                    }

                    if (insertedAmount > 0) {
                        ItemStack toRemove = item.clone();
                        toRemove.setAmount(insertedAmount);
                        removed.add(toRemove);
                        transferred++;
                    }
                }

                rangeStart += rangeSize;
            }

            if (!removed.isEmpty()) {
                spawner.removeItemsAndUpdateSellValue(removed);
                guiManager.updateSpawnerMenuViewers(spawner);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error transferring items from spawner to hopper at " + hopperLoc, ex);
        } finally {
            lock.unlock();
        }
    }
}
