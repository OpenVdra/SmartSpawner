package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Fired after the economy deposit and storage removal both succeed. */
public final class SpawnerSellCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location location;
    private final List<SoldItem> items;
    private final double amount;

    public SpawnerSellCompletedEvent(Player player, Location location, List<SoldItem> items, double amount) {
        this.player = player;
        this.location = location.clone();
        this.items = List.copyOf(items);
        this.amount = amount;
    }

    public Player getPlayer() { return player; }
    public Location getLocation() { return location.clone(); }
    public List<SoldItem> getItems() { return items; }
    public double getMoneyAmount() { return amount; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }

    /** One item identity and its full storage quantity. */
    public record SoldItem(ItemStack item, long quantity) {
        public SoldItem {
            if (item == null || quantity <= 0) throw new IllegalArgumentException("Invalid sold item");
            item = item.clone();
        }
        @Override public ItemStack item() { return item.clone(); }
    }
}
