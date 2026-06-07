package github.nighter.smartspawner.spawner.interactions.type;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerEggChangeEvent;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Handles interactions with spawn eggs on creature spawners
 */
public class SpawnEggHandler {
    private static final String PERMISSION_CHANGE_TYPE = "smartspawner.changetype";
    private static final String NO_PERMISSION_KEY = "no_permission";
    private static final String CHANGED_MESSAGE_KEY = "entity_changed";
    private static final String SPAWN_EGG_SUFFIX = "_SPAWN_EGG";

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final Map<Material, EntityType> eggTypeCache;

    /**
     * Constructs a new SpawnEggHandler
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnEggHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.eggTypeCache = new HashMap<>();
        initializeEggTypeCache();
    }

    /**
     * Handles a player using a spawn egg on a creature spawner
     *
     * @param player      The player using the spawn egg
     * @param spawner     The target creature spawner
     * @param spawnerData The spawner data
     * @param spawnEgg    The spawn egg item being used
     */
    public void handleSpawnEggUse(Player player, CreatureSpawner spawner, SpawnerData spawnerData, ItemStack spawnEgg) {
        // Validate parameters
        if (player == null || spawner == null || spawnerData == null || spawnEgg == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to handle spawn egg use with null parameters");
            return;
        }

        // Check permission
        if (!player.hasPermission(PERMISSION_CHANGE_TYPE)) {
            messageService.sendMessage(player, NO_PERMISSION_KEY);
            return;
        }

        // Get entity type from spawn egg
        Optional<EntityType> optionalEntityType = getEntityTypeFromSpawnEgg(spawnEgg.getType());

        if (optionalEntityType.isPresent()) {
            EntityType newType = optionalEntityType.get();
            updateSpawner(player, spawner, spawnerData, newType);
            consumeItemIfSurvival(player, spawnEgg);
        }
    }

    /**
     * Updates the spawner with the new entity type
     *
     * @param player      The player changing the spawner
     * @param spawner     The spawner to update
     * @param spawnerData The spawner data
     * @param newType     The new entity type
     */
    private void updateSpawner(Player player, CreatureSpawner spawner, SpawnerData spawnerData, EntityType newType) {
        if(SpawnerEggChangeEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerEggChangeEvent e = new SpawnerEggChangeEvent(player, spawner.getLocation(), spawnerData.getEntityType(), newType);
            Bukkit.getPluginManager().callEvent(e);
            if(e.isCancelled()) return;
        }

        // Update spawner data
        spawnerData.setEntityType(newType);
        spawnerData.updateLastInteractedPlayer(player.getName());

        // Update physical spawner
        spawner.setSpawnedType(newType);
        spawner.update();

        // Notify player
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", languageManager.getFormattedMobName(newType));
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(placeholders.get("entity")));
        messageService.sendMessage(player, CHANGED_MESSAGE_KEY, placeholders);
    }

    /**
     * Consumes one spawn egg if player is in survival mode
     *
     * @param player   The player
     * @param spawnEgg The spawn egg item
     */
    private void consumeItemIfSurvival(Player player, ItemStack spawnEgg) {
        if (player.getGameMode() == GameMode.SURVIVAL) {
            spawnEgg.setAmount(spawnEgg.getAmount() - 1);
        }
    }

    /**
     * Gets the entity type from a spawn egg material
     *
     * @param material The spawn egg material
     * @return Optional containing the entity type if valid, empty otherwise
     */
    private Optional<EntityType> getEntityTypeFromSpawnEgg(Material material) {
        // Check cache first
        if (eggTypeCache.containsKey(material)) {
            return Optional.of(eggTypeCache.get(material));
        }

        // Only process materials that end with _SPAWN_EGG
        if (!material.name().endsWith(SPAWN_EGG_SUFFIX)) {
            return Optional.empty();
        }

        try {
            String entityName = material.name().replace(SPAWN_EGG_SUFFIX, "");
            EntityType entityType = EntityType.valueOf(entityName);

            // Add to cache for future lookups
            eggTypeCache.put(material, entityType);
            return Optional.of(entityType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.FINE, "Failed to get entity type from material: " + material, e);
            return Optional.empty();
        }
    }

    /**
     * Pre-caches all valid spawn egg to entity type mappings
     */
    private void initializeEggTypeCache() {
        for (Material material : Material.values()) {
            if (material.name().endsWith(SPAWN_EGG_SUFFIX)) {
                try {
                    String entityName = material.name().replace(SPAWN_EGG_SUFFIX, "");
                    EntityType entityType = EntityType.valueOf(entityName);
                    eggTypeCache.put(material, entityType);
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid mappings
                }
            }
        }
    }
}
