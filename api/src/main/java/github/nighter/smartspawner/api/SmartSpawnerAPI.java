package github.nighter.smartspawner.api;

import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import github.nighter.smartspawner.api.data.SpawnerRemovalOptions;
import github.nighter.smartspawner.api.data.SpawnerRemovalResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Main API interface for SmartSpawner plugin.
 * This API allows other plugins to interact with SmartSpawner functionality.
 */
public interface SmartSpawnerAPI {

    /**
     * Creates a SmartSpawner item with the specified entity type.
     *
     * @param entityType the type of entity this spawner will spawn
     * @return an ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType);

    /**
     * Creates a SmartSpawner item with the specified entity type and a custom amount.
     *
     * @param entityType the type of entity this spawner will spawn
     * @param amount the amount of the item stack
     * @return an ItemStack representing the spawner
     */
    ItemStack createSpawnerItem(EntityType entityType, int amount);

    /**
     * Creates a vanilla spawner item without SmartSpawner features.
     *
     * @param entityType the type of entity this spawner will spawn
     * @return an ItemStack representing the vanilla spawner
     */
    ItemStack createVanillaSpawnerItem(EntityType entityType);

    /**
     * Creates a vanilla spawner item without SmartSpawner features.
     *
     * @param entityType the type of entity this spawner will spawn
     * @param amount the amount of the item stack
     * @return an ItemStack representing the vanilla spawner
     */
    ItemStack createVanillaSpawnerItem(EntityType entityType, int amount);

    /**
     * Creates an item spawner that spawns items instead of entities.
     *
     * @param itemMaterial the material type for the item spawner
     * @return an ItemStack representing the item spawner
     */
    ItemStack createItemSpawnerItem(Material itemMaterial);

    /**
     * Creates an item spawner that spawns items instead of entities.
     *
     * @param itemMaterial the material type for the item spawner
     * @param amount the amount of the item stack
     * @return an ItemStack representing the item spawner
     */
    ItemStack createItemSpawnerItem(Material itemMaterial, int amount);

    /**
     * Checks if an ItemStack is a SmartSpawner.
     *
     * @param item the ItemStack to check
     * @return true if the item is a SmartSpawner, false otherwise
     */
    boolean isSmartSpawner(ItemStack item);

    /**
     * Checks if an ItemStack is a vanilla spawner.
     *
     * @param item the ItemStack to check
     * @return true if the item is a vanilla spawner, false otherwise
     */
    boolean isVanillaSpawner(ItemStack item);

    /**
     * Checks if an ItemStack is an item spawner.
     *
     * @param item the ItemStack to check
     * @return true if the item is an item spawner, false otherwise
     */
    boolean isItemSpawner(ItemStack item);

    /**
     * Gets the entity type from a spawner item.
     *
     * @param item the spawner ItemStack
     * @return the EntityType of the spawner, or null if not a valid spawner
     */
    EntityType getSpawnerEntityType(ItemStack item);

    /**
     * Gets the item material from an item spawner.
     *
     * @param item the item spawner ItemStack
     * @return the Material that the item spawner spawns, or null if not a valid item spawner
     */
    Material getItemSpawnerMaterial(ItemStack item);

    /**
     * Gets spawner data by location.
     * The returned DTO is read-only. To modify spawner properties, use {@link #getSpawnerModifier(String)}.
     *
     * @param location the location of the spawner block
     * @return the spawner data DTO, or null if no spawner exists at that location
     */
    SpawnerDataDTO getSpawnerByLocation(Location location);

    /**
     * Gets spawner data by unique identifier.
     * The returned DTO is read-only. To modify spawner properties, use {@link #getSpawnerModifier(String)}.
     *
     * @param spawnerId the unique ID of the spawner
     * @return the spawner data DTO, or null if spawner with that ID doesn't exist
     */
    SpawnerDataDTO getSpawnerById(String spawnerId);

    /**
     * Gets all registered spawners in the server.
     * The returned DTOs are read-only. To modify spawner properties, use {@link #getSpawnerModifier(String)}.
     *
     * @return list of all spawner data DTOs
     */
    List<SpawnerDataDTO> getAllSpawners();

    /**
     * Creates a modifier for the specified spawner to change its properties.
     * Use this to modify spawner values and then call {@link SpawnerDataModifier#applyChanges()}
     * to recalculate and apply the changes.
     *
     * @param spawnerId the unique ID of the spawner
     * @return a spawner data modifier, or null if spawner doesn't exist
     */
    SpawnerDataModifier getSpawnerModifier(String spawnerId);

    /**
     * Removes a SmartSpawner cage at the given block location.
     * <p>
     * Must be called on the correct region thread for the target location (Paper/Folia).
     *
     * @param location the block location of the spawner cage
     * @return the removal result
     */
    SpawnerRemovalResult removeSpawner(Location location);

    /**
     * Removes a SmartSpawner cage at the given block location with custom options.
     * <p>
     * Must be called on the correct region thread for the target location (Paper/Folia).
     *
     * @param location the block location of the spawner cage
     * @param options removal options such as sell/claim behavior
     * @return the removal result
     */
    SpawnerRemovalResult removeSpawner(Location location, SpawnerRemovalOptions options);

    /**
     * Removes a SmartSpawner cage by its unique identifier.
     * <p>
     * Must be called on the correct region thread for the spawner's location (Paper/Folia).
     *
     * @param spawnerId the unique spawner identifier
     * @return the removal result
     */
    SpawnerRemovalResult removeSpawner(String spawnerId);

    /**
     * Removes a SmartSpawner cage by its unique identifier with custom options.
     * <p>
     * Must be called on the correct region thread for the spawner's location (Paper/Folia).
     *
     * @param spawnerId the unique spawner identifier
     * @param options removal options such as sell/claim behavior
     * @return the removal result
     */
    SpawnerRemovalResult removeSpawner(String spawnerId, SpawnerRemovalOptions options);
}