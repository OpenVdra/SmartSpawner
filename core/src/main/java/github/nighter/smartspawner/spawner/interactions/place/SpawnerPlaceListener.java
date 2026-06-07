package github.nighter.smartspawner.spawner.interactions.place;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.config.Config;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.hooks.protections.CheckStackBlock;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.utils.SpawnerTypeChecker;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerPlaceListener implements Listener {
    private static final double PARTICLE_OFFSET = 0.5;
    private static final long PLACEMENT_COOLDOWN_MS = 100;

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final Map<UUID, Long> lastPlacementTime = new ConcurrentHashMap<>();

    public SpawnerPlaceListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();

        if (!checkPlacementCooldown(player)) {
            event.setCancelled(true);
            return;
        }

        if (!(meta instanceof BlockStateMeta blockMeta)) {
            event.setCancelled(true);
            return;
        }

        if (!CheckStackBlock.CanPlayerPlaceBlock(player, block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        boolean isVanillaSpawner = SpawnerTypeChecker.isVanillaSpawner(item);

        if (!verifyPlayerInventory(player, item, isVanillaSpawner)) {
            event.setCancelled(true);
            return;
        }

        int stackSize = calculateStackSize(player, item, isVanillaSpawner);

        EntityType storedEntityType = null;
        Material itemSpawnerMaterial = null;
        
        if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner) {
            storedEntityType = ((CreatureSpawner) blockMeta.getBlockState()).getSpawnedType();
            
            // Check if this is an item spawner
            if (storedEntityType == EntityType.ITEM && meta.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "item_spawner_material"), PersistentDataType.STRING)) {
                String materialName = meta.getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "item_spawner_material"), PersistentDataType.STRING);
                if (materialName != null) {
                    try {
                        itemSpawnerMaterial = Material.valueOf(materialName);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid item spawner material: " + materialName);
                    }
                }
            }
        }

        if(SpawnerPlaceEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerPlaceEvent e = new SpawnerPlaceEvent(player, block.getLocation(), storedEntityType, stackSize);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        if (!immediatelyConsumeItems(player, item, stackSize)) {
            event.setCancelled(true);
            return;
        }

        handleSpawnerSetup(block, player, storedEntityType, isVanillaSpawner, stackSize, itemSpawnerMaterial);
    }

    private boolean checkPlacementCooldown(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastPlacementTime.get(player.getUniqueId());

        if (lastTime != null && (currentTime - lastTime) < PLACEMENT_COOLDOWN_MS) {
            return false;
        }

        lastPlacementTime.put(player.getUniqueId(), currentTime);
        return true;
    }

    private boolean verifyPlayerInventory(Player player, ItemStack item, boolean isVanillaSpawner) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        if (isVanillaSpawner) {
            return item.getAmount() >= 1;
        }

        if (player.isSneaking()) {
            int requiredAmount = item.getAmount();

            int totalItems = 0;
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.isSimilar(item)) {
                    totalItems += invItem.getAmount();
                }
            }

            return totalItems >= requiredAmount;
        }

        return item.getAmount() >= 1;
    }

    private boolean immediatelyConsumeItems(Player player, ItemStack item, int stackSize) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        // For stack size 1, Bukkit will automatically consume the item, so we don't need to do anything
        if (stackSize <= 1) {
            return true;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int remainingToConsume = stackSize;

        // Find and consume the additional items needed
        for (int i = 0; i < contents.length && remainingToConsume > 0; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.isSimilar(item)) {
                int amountInSlot = slot.getAmount();
                int toRemove = Math.min(remainingToConsume, amountInSlot);

                if (toRemove >= amountInSlot) {
                    contents[i] = null;
                } else {
                    slot.setAmount(amountInSlot - toRemove);
                }

                remainingToConsume -= toRemove;
            }
        }

        if (remainingToConsume > 0) {
            plugin.debug("Could not consume enough items for player " + player.getName() +
                    ". Remaining: " + remainingToConsume + ", Stack size requested: " + stackSize);
            return false;
        }

        player.getInventory().setContents(contents);
        player.updateInventory();

        return true;
    }

    private int calculateStackSize(Player player, ItemStack item, boolean isVanillaSpawner) {
        if (isVanillaSpawner) {
            return 1;
        }

        if (player.isSneaking()) {
            return Math.min(item.getAmount(), plugin.getConfig().getInt("spawner_properties.default.max_stack_size", 10000));
        } else {
            return 1;
        }
    }

    private void handleSpawnerSetup(Block block, Player player, EntityType entityType,
                                    boolean isVanillaSpawner, int stackSize, Material itemSpawnerMaterial) {
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState(false);

        if (isVanillaSpawner) {
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
            return;
        }

        Scheduler.runLocationTaskLater(block.getLocation(), () -> {
            if (block.getType() != Material.SPAWNER) {
                return;
            }

            CreatureSpawner delayedSpawner = (CreatureSpawner) block.getState(false);
            
            // Handle item spawners differently
            if (entityType == EntityType.ITEM && itemSpawnerMaterial != null) {
                // Set up item spawner
                delayedSpawner.setSpawnedType(EntityType.ITEM);
                
                // Create an ItemStack for the spawner to spawn
                ItemStack spawnedItem = new ItemStack(itemSpawnerMaterial, 1);
                delayedSpawner.setSpawnedItem(spawnedItem);
                delayedSpawner.update(true, false);
                
                createSmartItemSpawner(block, player, itemSpawnerMaterial, stackSize);
            } else {
                // Handle regular entity spawners
                EntityType finalEntityType = getEntityType(entityType, delayedSpawner);

                delayedSpawner.setSpawnedType(finalEntityType);
                delayedSpawner.update(true, false);
                createSmartSpawner(block, player, finalEntityType, stackSize);
            }

            setupHopperIntegration(block);
        }, 2L);
    }

    private EntityType getEntityType(EntityType storedEntityType, CreatureSpawner placedSpawner) {
        EntityType entityType = storedEntityType;

        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = placedSpawner.getSpawnedType();

            placedSpawner.setSpawnedType(entityType);
            placedSpawner.update(true, false);
        }

        return entityType;
    }

    private void createSmartSpawner(Block block, Player player, EntityType entityType, int stackSize) {
        // Check if a spawner already exists at this location (prevent duplicates/ghost spawners)
        SpawnerData existingSpawner = spawnerManager.getSpawnerByLocation(block.getLocation());
        if (existingSpawner != null) {
            plugin.debug("Spawner already exists at " + block.getLocation() + " with ID " + existingSpawner.getSpawnerId());
            return;
        }

        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        BlockState state = block.getState(false);
        if (state instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
        }

        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);
        spawner.setStackSize(stackSize);

        // Track player interaction for last interaction field
        spawner.updateLastInteractedPlayer(player.getName());
        spawnerManager.addSpawner(spawnerId, spawner);
        spawnerManager.queueSpawnerForSaving(spawnerId);

        if (Config.get().isSpawnerActivateParticlesEnabled()) {
            showActivationParticles(block);
        }

        messageService.sendMessage(player, "spawner_activated");
    }

    private void createSmartItemSpawner(Block block, Player player, Material itemMaterial, int stackSize) {
        // Check if a spawner already exists at this location (prevent duplicates/ghost spawners)
        SpawnerData existingSpawner = spawnerManager.getSpawnerByLocation(block.getLocation());
        if (existingSpawner != null) {
            plugin.debug("Item spawner already exists at " + block.getLocation() + " with ID " + existingSpawner.getSpawnerId());
            return;
        }

        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        BlockState state = block.getState(false);
        if (state instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(EntityType.ITEM);
            // Set the item to spawn
            ItemStack spawnedItem = new ItemStack(itemMaterial, 1);
            spawner.setSpawnedItem(spawnedItem);
            spawner.update(true, false);
        }

        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), itemMaterial, plugin);
        spawner.setSpawnerActive(true);
        spawner.setStackSize(stackSize);

        // Track player interaction for last interaction field
        spawner.updateLastInteractedPlayer(player.getName());

        spawnerManager.addSpawner(spawnerId, spawner);
        spawnerManager.queueSpawnerForSaving(spawnerId);

        if (Config.get().isSpawnerActivateParticlesEnabled()) {
            showActivationParticles(block);
        }

        messageService.sendMessage(player, "spawner_activated");
    }

    private void showActivationParticles(Block block) {
        Scheduler.runLocationTask(block.getLocation(), () -> {
            Location particleLocation = block.getLocation().clone().add(
                    PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET);
            block.getWorld().spawnParticle(
                    Particle.WITCH,
                    particleLocation,
                    50, PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET, 0
            );
        });
    }

    private void setupHopperIntegration(Block block) {
        HopperService currentHopperService = plugin.getHopperService();
        if (currentHopperService == null || !plugin.getHopperConfig().isHopperEnabled()) {
            return;
        }

        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER) {
            currentHopperService.getTracker().tryAdd(blockBelow);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    public void cleanupPlayer(UUID playerId) {
        lastPlacementTime.remove(playerId);
    }
}
