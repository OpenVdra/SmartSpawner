package github.nighter.smartspawner.api.data;

/**
 * Describes why a SmartSpawner cage is being removed programmatically.
 */
public enum SpawnerRemovalReason {

    /**
     * Removed through the public API without a more specific reason.
     */
    API,

    /**
     * Removed because a timed lease or expiry policy elapsed.
     */
    EXPIRED,

    /**
     * Removed by an administrator or admin tooling.
     */
    ADMIN,

    /**
     * Removed for another plugin-defined reason.
     */
    OTHER
}
