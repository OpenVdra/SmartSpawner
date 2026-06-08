package github.nighter.smartspawner.api.data;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Options for programmatic spawner removal through {@link github.nighter.smartspawner.api.SmartSpawnerAPI}.
 */
public final class SpawnerRemovalOptions {

    private final SpawnerRemovalReason reason;
    private final boolean sellAndClaimExp;
    private final Player payoutPlayer;

    private SpawnerRemovalOptions(Builder builder) {
        this.reason = builder.reason;
        this.sellAndClaimExp = builder.sellAndClaimExp;
        this.payoutPlayer = builder.payoutPlayer;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull SpawnerRemovalOptions defaults() {
        return builder().build();
    }

    public static @NotNull SpawnerRemovalOptions expired(@Nullable Player payoutPlayer) {
        return builder()
                .reason(SpawnerRemovalReason.EXPIRED)
                .sellAndClaimExp(true)
                .payoutPlayer(payoutPlayer)
                .build();
    }

    public @NotNull SpawnerRemovalReason getReason() {
        return reason;
    }

    public boolean isSellAndClaimExp() {
        return sellAndClaimExp;
    }

    public @Nullable Player getPayoutPlayer() {
        return payoutPlayer;
    }

    public static final class Builder {
        private SpawnerRemovalReason reason = SpawnerRemovalReason.API;
        private boolean sellAndClaimExp;
        private Player payoutPlayer;

        private Builder() {
        }

        public @NotNull Builder reason(@NotNull SpawnerRemovalReason reason) {
            this.reason = reason;
            return this;
        }

        public @NotNull Builder sellAndClaimExp(boolean sellAndClaimExp) {
            this.sellAndClaimExp = sellAndClaimExp;
            return this;
        }

        public @NotNull Builder payoutPlayer(@Nullable Player payoutPlayer) {
            this.payoutPlayer = payoutPlayer;
            return this;
        }

        public @NotNull SpawnerRemovalOptions build() {
            return new SpawnerRemovalOptions(this);
        }
    }
}
