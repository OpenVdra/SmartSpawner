package github.nighter.smartspawner.utils;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.DataComponentTypeKeys;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Set;

public final class ItemTooltipUtil {
    private static final Set<DataComponentType> HIDDEN_TOOLTIP_COMPONENTS = Set.of(
            getDataComponentType(DataComponentTypeKeys.BLOCK_ENTITY_DATA)
    );

    private static final Set<DataComponentType> HIDDEN_BUNDLE_TOOLTIP_COMPONENTS = Set.of(
            getDataComponentType(DataComponentTypeKeys.BUNDLE_CONTENTS)
    );

    private ItemTooltipUtil() {
    }

    public static void hideTooltip(ItemStack item) {
        hideTooltipComponents(item, HIDDEN_TOOLTIP_COMPONENTS);
    }

    public static void hideBundleTooltip(ItemStack item) {
        hideTooltipComponents(item, HIDDEN_BUNDLE_TOOLTIP_COMPONENTS);
    }

    private static DataComponentType getDataComponentType(TypedKey<DataComponentType> key) {
        return Objects.requireNonNull(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.DATA_COMPONENT_TYPE)
                .get(key));
    }

    private static void hideTooltipComponents(ItemStack item, Set<DataComponentType> hiddenComponents) {
        if (item == null) {
            return;
        }

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().hiddenComponents(hiddenComponents).build());
    }
}
