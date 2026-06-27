package github.nighter.smartspawner.spawner.sell;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SellResult {
    @Getter
    private final double totalValue;
    @Getter
    private final long itemsSold;
    @Getter
    private final Map<ItemSignature, Long> itemsToRemove;
    @Getter
    private final long timestamp;
    @Getter
    private final boolean successful;

    public SellResult(double totalValue, long itemsSold, Map<ItemSignature, Long> itemsToRemove) {
        this.totalValue = totalValue;
        this.itemsSold = itemsSold;
        this.itemsToRemove = new HashMap<>(itemsToRemove);
        this.timestamp = System.currentTimeMillis();
        this.successful = totalValue > 0.0 && !itemsToRemove.isEmpty();
    }

    public static SellResult empty() {
        return new SellResult(0.0, 0, Collections.emptyMap());
    }

    public boolean hasItems() {
        return !itemsToRemove.isEmpty();
    }
}
