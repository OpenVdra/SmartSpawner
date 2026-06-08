package github.nighter.smartspawner.api.events;

import github.nighter.smartspawner.api.data.SpawnerRemovalReason;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called before a SmartSpawner cage is removed programmatically through the public API.
 * <p>
 * This is distinct from {@link SpawnerRemoveEvent}, which fires when spawners are unstacked
 * from the stacker GUI.
 */
@Getter
public class SpawnerDestroyEvent extends SpawnerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final SpawnerRemovalReason reason;
    private final EntityType entityType;
    private final Player initiator;
    private final Player payoutPlayer;

    @Setter
    private boolean cancelled = false;

    public SpawnerDestroyEvent(
            @NotNull Location location,
            int quantity,
            @NotNull SpawnerRemovalReason reason,
            @Nullable EntityType entityType,
            @Nullable Player initiator,
            @Nullable Player payoutPlayer
    ) {
        super(location, quantity);
        this.reason = reason;
        this.entityType = entityType;
        this.initiator = initiator;
        this.payoutPlayer = payoutPlayer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
