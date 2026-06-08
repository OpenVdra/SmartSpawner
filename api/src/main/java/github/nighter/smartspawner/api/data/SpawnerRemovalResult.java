package github.nighter.smartspawner.api.data;

/**
 * Result of a programmatic spawner removal request.
 */
public enum SpawnerRemovalResult {

    /**
     * The spawner block and data were removed successfully.
     */
    SUCCESS,

    /**
     * No SmartSpawner cage exists at the requested location or ID.
     */
    NOT_FOUND,

    /**
     * A {@link github.nighter.smartspawner.api.events.SpawnerDestroyEvent} listener cancelled removal.
     */
    CANCELLED,

    /**
     * Another operation is already in progress at this spawner location.
     */
    LOCKED,

    /**
     * Stored items are being sold asynchronously; cleanup runs after sell completes.
     */
    SELL_PENDING,

    /**
     * Removal failed for an unexpected reason.
     */
    FAILED
}
