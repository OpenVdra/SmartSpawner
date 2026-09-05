package github.nighter.smartspawner.spawner.data.database;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.config.SpawnerDisplayConfigurator;
import github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Translates between a spawner table row and {@link SpawnerData}: {@link #bind} for writes,
 * {@link #fromResultSet} for reads. Holds no connection or per-flush state, so one instance is shared
 * by every read and write path.
 */
final class SpawnerRowMapper {

    private final SmartSpawner plugin;
    private final Logger logger;

    SpawnerRowMapper(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Binds one spawner onto an upsert statement: {@code spawner_id} at 1, then the 27 value columns
     * in {@link SpawnerSql}'s order (world through config_name) at 2..28.
     *
     * @return {@code false} when the virtual inventory could not be encoded. The caller must then skip
     *         and re-queue the row: writing it would replace the stored items with an empty blob.
     */
    boolean bind(PreparedStatement stmt, SpawnerData spawner) throws SQLException {
        VirtualInventory inventory = spawner.getVirtualInventory();
        byte[] items = null;
        long totalItems = 0L;
        if (inventory != null) {
            Map<ItemSignature, Long> consolidated = inventory.getConsolidatedItems();
            try {
                items = SpawnerInventoryCodec.encode(consolidated);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not encode inventory for spawner " + spawner.getSpawnerId()
                        + ", skipping this save so the stored items are not lost", e);
                return false;
            }
            totalItems = SpawnerInventoryCodec.totalItems(consolidated);
        }

        Location loc = spawner.getSpawnerLocation();
        int i = 1;
        stmt.setString(i++, spawner.getSpawnerId());
        stmt.setString(i++, loc.getWorld().getName());
        stmt.setInt(i++, loc.getBlockX());
        stmt.setInt(i++, loc.getBlockY());
        stmt.setInt(i++, loc.getBlockZ());
        stmt.setInt(i++, loc.getBlockX() >> 4);
        stmt.setInt(i++, loc.getBlockZ() >> 4);
        stmt.setString(i++, spawner.getEntityType().name());
        stmt.setString(i++, spawner.isItemSpawner() ? spawner.getSpawnedItemMaterial().name() : null);
        stmt.setInt(i++, spawner.getStackSize());
        stmt.setInt(i++, spawner.getMaxStackSize());
        stmt.setBoolean(i++, spawner.getSpawnerActive());
        stmt.setBoolean(i++, spawner.getSpawnerStop().get());
        stmt.setInt(i++, spawner.getSpawnerRange());
        stmt.setLong(i++, spawner.getSpawnDelay());
        stmt.setLong(i++, spawner.getLastSpawnTime());
        stmt.setInt(i++, spawner.getMinMobs());
        stmt.setInt(i++, spawner.getMaxMobs());
        stmt.setInt(i++, spawner.getMaxSpawnerLootSlots());
        stmt.setBoolean(i++, spawner.getIsAtCapacity());
        stmt.setLong(i++, totalItems);
        stmt.setLong(i++, Math.max(0L, spawner.getSpawnerExp()));
        stmt.setLong(i++, spawner.getMaxStoredExp());
        stmt.setString(i++, spawner.getLastInteractedPlayer());
        stmt.setString(i++, spawner.getPreferredSortItem() != null ? spawner.getPreferredSortItem().name() : null);
        stmt.setString(i++, serializeFilteredItems(spawner.getFilteredItems()));
        stmt.setBytes(i++, items);
        stmt.setString(i, spawner.getConfigName());
        return true;
    }

    /**
     * Reconstructs a spawner from the current row.
     *
     * @return {@code null} when the world is not loaded yet or a stored enum no longer resolves. A
     *         null world is expected and handled by the caller (see
     *         {@code SpawnerStorage.loadAllSpawnersRaw}); a bad enum is logged and skipped.
     */
    SpawnerData fromResultSet(ResultSet rs) throws SQLException {
        String spawnerId = rs.getString("spawner_id");

        World world = Bukkit.getWorld(rs.getString("world"));
        if (world == null) {
            return null;
        }
        Location location = new Location(world, rs.getInt("loc_x"), rs.getInt("loc_y"), rs.getInt("loc_z"));

        String entityTypeStr = rs.getString("entity_type");
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityTypeStr);
        } catch (IllegalArgumentException e) {
            logger.severe("Invalid entity type for spawner " + spawnerId + ": " + entityTypeStr);
            return null;
        }

        String configName = rs.getString("config_name");
        String itemMaterialStr = rs.getString("itemspawner_type");
        SpawnerData spawner;
        if (entityType == EntityType.ITEM && itemMaterialStr != null) {
            try {
                spawner = new SpawnerData(spawnerId, location, Material.valueOf(itemMaterialStr), configName, plugin);
            } catch (IllegalArgumentException e) {
                logger.severe("Invalid item spawner material for spawner " + spawnerId + ": " + itemMaterialStr);
                return null;
            }
        } else {
            spawner = new SpawnerData(spawnerId, location, entityType, configName, plugin);
        }

        applyStoredSettings(rs, spawner);
        applyStoredInventory(rs, spawner);
        restoreCagePreview(location, spawner);
        return spawner;
    }

    private void applyStoredSettings(ResultSet rs, SpawnerData spawner) throws SQLException {
        spawner.setSpawnerExpData(rs.getLong("exp"));
        spawner.setSpawnerActive(rs.getBoolean("active"));
        spawner.setSpawnerRange(rs.getInt("activation_range"));
        spawner.getSpawnerStop().set(rs.getBoolean("stop"));
        spawner.setSpawnDelay(Math.max(1L, rs.getLong("delay")));
        spawner.setMaxSpawnerLootSlots(rs.getInt("max_loot_slots"));
        spawner.setMaxStoredExp(rs.getLong("max_stored_exp"));
        spawner.setMinMobs(rs.getInt("min_mobs"));
        spawner.setMaxMobs(rs.getInt("max_mobs"));
        spawner.setMaxStackSize(rs.getInt("max_stack_size"));
        // Don't restart the hopper during a batch load.
        spawner.setStackSize(rs.getInt("stack_size"), false);
        spawner.setLastSpawnTime(rs.getLong("last_spawn_time"));
        spawner.setIsAtCapacity(rs.getBoolean("is_at_capacity"));
        spawner.setLastInteractedPlayer(rs.getString("last_interacted_player"));

        String preferredSortItemStr = rs.getString("preferred_sort_item");
        if (preferredSortItemStr != null && !preferredSortItemStr.isEmpty()) {
            try {
                spawner.setPreferredSortItem(Material.valueOf(preferredSortItemStr));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid preferred sort item for spawner "
                        + spawner.getSpawnerId() + ": " + preferredSortItemStr);
            }
        }

        String filteredItemsStr = rs.getString("filtered_items");
        if (filteredItemsStr != null && !filteredItemsStr.isEmpty()) {
            deserializeFilteredItems(filteredItemsStr, spawner.getFilteredItems());
        }
    }

    private void applyStoredInventory(ResultSet rs, SpawnerData spawner) throws SQLException {
        VirtualInventory inventory = new VirtualInventory(spawner.getMaxSpawnerLootSlots());
        byte[] itemsBlob = rs.getBytes("storage_items");
        if (itemsBlob != null && itemsBlob.length > 0) {
            try {
                Map<ItemStack, Long> items = SpawnerInventoryCodec.decode(itemsBlob);
                for (Map.Entry<ItemStack, Long> entry : items.entrySet()) {
                    inventory.addConsolidatedItem(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                logger.warning("Error loading inventory for spawner "
                        + spawner.getSpawnerId() + ": " + e.getMessage());
            }
        }
        spawner.setVirtualInventory(inventory);
        spawner.markSellValueDirty();

        if (spawner.getPreferredSortItem() != null) {
            inventory.sortItems(spawner.getPreferredSortItem());
        }
    }

    /**
     * Rebuilds the in-cage display model from config once the block's region is loaded, so an
     * item-spawner or a mob with captured NBT does not render as a blank cage after a restart.
     */
    private void restoreCagePreview(Location location, SpawnerData spawner) {
        Scheduler.runLocationTask(location, () -> {
            Block block = location.getBlock();
            if (block.getType() != Material.SPAWNER) {
                return;
            }
            BlockState state = block.getState(false);
            if (state instanceof CreatureSpawner cs) {
                if (spawner.isItemSpawner()) {
                    SpawnerDisplayConfigurator.applyItem(
                            plugin, cs, spawner.getConfigName(), spawner.getSpawnedItemMaterial());
                } else {
                    SpawnerDisplayConfigurator.applyMob(
                            plugin, cs, spawner.getConfigName(), spawner.getEntityType());
                }
                cs.update(true, false);
            }
        });
    }

    private String serializeFilteredItems(Set<Material> filteredItems) {
        if (filteredItems == null || filteredItems.isEmpty()) {
            return null;
        }
        return filteredItems.stream().map(Material::name).collect(Collectors.joining(","));
    }

    private void deserializeFilteredItems(String data, Set<Material> filteredItems) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (String materialName : data.split(",")) {
            try {
                filteredItems.add(Material.valueOf(materialName.trim()));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material in filtered items: " + materialName);
            }
        }
    }
}
