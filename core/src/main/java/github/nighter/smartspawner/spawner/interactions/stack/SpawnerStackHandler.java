package github.nighter.smartspawner.spawner.interactions.stack;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import github.nighter.smartspawner.config.Config;
import github.nighter.smartspawner.hooks.protections.CheckStackBlock;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.utils.SpawnerTypeChecker;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStackHandler {
    private static final long STACK_COOLDOWN = 250L; // 250ms cooldown between stacks

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final github.nighter.smartspawner.spawner.data.SpawnerManager spawnerManager;
    private final Map<UUID, Long> lastStackTime;
    private final Map<Location, UUID> stackLocks;

    public SpawnerStackHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.lastStackTime = new ConcurrentHashMap<>();
        this.stackLocks = new ConcurrentHashMap<>();

        // Start cleanup task
        startCleanupTask();
    }

    private void startCleanupTask() {
        Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            // Clear entries older than 10 seconds
            lastStackTime.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
            // Remove locks for offline players
            stackLocks.entrySet().removeIf(entry -> plugin.getServer().getPlayer(entry.getValue()) == null);
        }, 200L, 200L); // Run every 10 seconds
    }

    public void handleSpawnerStacking(Player player, Block block, SpawnerData spawnerData, ItemStack itemInHand) {
        // Block stacking while a sell is in progress
        if (spawnerData.isSelling()) {
            messageService.sendMessage(player, "action_in_progress");
            return;
        }

        // Anti-spam check
        if (isOnCooldown(player)) {
            return;
        }

        // Try to acquire lock
        if (!acquireStackLock(player, block.getLocation())) {
            return;
        }

        try {
            handleSpawnerStack(player, spawnerData, itemInHand, player.isSneaking());
        } finally {
            releaseStackLock(block.getLocation());
            updateLastStackTime(player);
        }
    }

    private boolean isOnCooldown(Player player) {
        long lastTime = lastStackTime.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastTime < STACK_COOLDOWN;
    }

    private void updateLastStackTime(Player player) {
        lastStackTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean acquireStackLock(Player player, Location location) {
        return stackLocks.putIfAbsent(location, player.getUniqueId()) == null;
    }

    private void releaseStackLock(Location location) {
        stackLocks.remove(location);
    }

    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        // Basic validation checks
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        Location location = targetSpawner.getSpawnerLocation();
        if (!hasStackPermissions(player, location)) {
            return false;
        }

        // Check if either the target or the item is a vanilla spawner
        if (SpawnerTypeChecker.isVanillaSpawner(itemInHand)) {
            messageService.sendMessage(player, "spawner_different");
            return false;
        }

        // Check if the item is an item spawner
        boolean isItemSpawnerItem = SpawnerTypeChecker.isItemSpawner(itemInHand);
        boolean isTargetItemSpawner = targetSpawner.isItemSpawner();

        // Both must be item spawners or both must be regular spawners
        if (isItemSpawnerItem != isTargetItemSpawner) {
            messageService.sendMessage(player, "spawner_different");
            return false;
        }

        // If both are item spawners, check if they spawn the same item
        if (isItemSpawnerItem && isTargetItemSpawner) {
            Material handItemMaterial = SpawnerTypeChecker.getItemSpawnerMaterial(itemInHand);
            Material targetItemMaterial = targetSpawner.getSpawnedItemMaterial();

            if (handItemMaterial == null || targetItemMaterial == null || handItemMaterial != targetItemMaterial) {
                messageService.sendMessage(player, "spawner_different");
                return false;
            }
        } else {
            // For regular spawners, check entity type
            // Always check the entity type directly without caching
            Optional<EntityType> handEntityTypeOpt = getEntityTypeFromItem(itemInHand);
            if (!handEntityTypeOpt.isPresent()) {
                messageService.sendMessage(player, "spawner_different");
                return false;
            }

            EntityType handEntityType = handEntityTypeOpt.get();
            EntityType targetEntityType = targetSpawner.getEntityType();

            // Verify types match
            if (handEntityType != targetEntityType) {
                messageService.sendMessage(player, "spawner_different");
                return false;
            }
        }

        // Verify stack limits
        int maxStackSize = targetSpawner.getMaxStackSize();
        int currentStack = targetSpawner.getStackSize();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("max", String.valueOf(maxStackSize));
        if (currentStack >= maxStackSize) {
            messageService.sendMessage(player, "spawner_stack_full", placeholders);
            return false;
        }

        return processStackAddition(player, targetSpawner, itemInHand, stackAll, currentStack, maxStackSize);
    }

    private boolean hasStackPermissions(Player player, Location location) {
        if (!CheckStackBlock.CanPlayerPlaceBlock(player, location)) {
            messageService.sendMessage(player, "spawner_protected");
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return false;
        }

        return true;
    }

    public Optional<EntityType> getEntityTypeFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        // Get entity type from block state (most reliable method)
        if (meta instanceof BlockStateMeta blockMeta) {
            if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner handSpawner) {
                EntityType entityType = handSpawner.getSpawnedType();
                if (entityType != null) {
                    return Optional.of(entityType);
                }
            }
        }

        return Optional.empty();
    }

    private boolean processStackAddition(Player player, SpawnerData targetSpawner, ItemStack itemInHand,
                                         boolean stackAll, int currentStack, int maxStackSize) {
        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack = stackAll ? Math.min(spaceLeft, itemAmount) : 1;
        int newStack = currentStack + amountToStack;

        if(SpawnerStackEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerStackEvent e = new SpawnerStackEvent(player, targetSpawner.getSpawnerLocation(), currentStack, newStack,
                    SpawnerStackEvent.StackSource.PLACE, targetSpawner.getEntityType());
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) return false;
        }

        // Update spawner data
        targetSpawner.setStackSize(newStack);

        // Mark spawner as modified for database save
        spawnerManager.markSpawnerModified(targetSpawner.getSpawnerId());

        if (targetSpawner.getIsAtCapacity()) {
            targetSpawner.setIsAtCapacity(false);
        }
        
        // Track player interaction for last interaction field
        targetSpawner.updateLastInteractedPlayer(player.getName());

        // Update player's inventory
        updatePlayerInventory(player, itemInHand, amountToStack);

        // Visual feedback
        showStackAnimation(targetSpawner, newStack, player);

        return true;
    }

    private void updatePlayerInventory(Player player, ItemStack itemInHand, int amountUsed) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            int remainingAmount = itemInHand.getAmount() - amountUsed;

            if (remainingAmount <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(remainingAmount);
            }
        }
    }

    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        if (Config.get().isSpawnerStackParticlesEnabled()) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();

            if (world != null) {
                // Use location-based scheduling for particle effects
                Scheduler.runLocationTask(loc, () -> {
                    world.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            loc.clone().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0
                    );
                });
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(newStack));
        messageService.sendMessage(player, "spawner_stack_success", placeholders);
    }
}
