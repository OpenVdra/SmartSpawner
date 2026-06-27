package github.nighter.smartspawner.spawner.gui.storage.button;

import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.utils.LRUCache;
import github.nighter.smartspawner.spawner.lootgen.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

public final class SortButton {

    private static final int SORT_BUTTON_CACHE_SIZE = 256;
    private static final LRUCache<SortButtonCacheKey, ItemStack> SORT_BUTTON_CACHE = new LRUCache<>(SORT_BUTTON_CACHE_SIZE);

    private static final EnumMap<Material, String> MATERIAL_NAME_CACHE = new EnumMap<>(Material.class);

    private SortButton() {}

    public static ItemStack getOrBuildSortButton(SpawnerData spawner, Material buttonMaterial,
                                                 LanguageManager languageManager, Function<ButtonData, ItemStack> buttonFactory) {

        EntityLootConfig lootConfig = spawner.getLootConfig();

        return SORT_BUTTON_CACHE.get(
                new SortButtonCacheKey(
                        lootConfig,
                        spawner.getPreferredSortItem(),
                        buttonMaterial
                ),
                key -> buildSortButton(
                        lootConfig,
                        key.selectedMaterial,
                        key.buttonMaterial,
                        languageManager,
                        buttonFactory
                )
        );
    }

    private static ItemStack buildSortButton(EntityLootConfig lootConfig, Material currentSort, Material buttonMaterial,
                                             LanguageManager languageManager, Function<ButtonData, ItemStack> buttonFactory) {

        String selectedItemFormat = languageManager.getGuiItemName("sort_items_button.selected_item");
        String unselectedItemFormat = languageManager.getGuiItemName("sort_items_button.unselected_item");
        String noneText = languageManager.getGuiItemName("sort_items_button.no_item");

        String availableItemsString;

        if (lootConfig != null && lootConfig.getAllItems() != null && !lootConfig.getAllItems().isEmpty()) {

            List<LootItem> sortedLoot = new ArrayList<>(lootConfig.getAllItems());

            sortedLoot.sort(Comparator.comparing(item -> item.material().name()));

            StringBuilder availableItems = new StringBuilder(sortedLoot.size() * 32);

            boolean first = true;

            for (LootItem lootItem : sortedLoot) {
                Material lootMaterial = lootItem.material();

                if (!first) {
                    availableItems.append('\n');
                }

                String itemName = MATERIAL_NAME_CACHE.computeIfAbsent(
                        lootMaterial,
                        languageManager::getVanillaItemName
                );

                String format = currentSort == lootMaterial
                                ? selectedItemFormat
                                : unselectedItemFormat;

                availableItems.append(format.replace("{item_name}", itemName));

                first = false;
            }

            availableItemsString = availableItems.toString();
        } else {
            availableItemsString = noneText;
        }

        Map<String, String> placeholders = new HashMap<>(1);
        placeholders.put("available_items", availableItemsString);

        return buttonFactory.apply(
                new ButtonData(buttonMaterial,
                        languageManager.getGuiItemName("sort_items_button.name", placeholders),
                        languageManager.getGuiItemLoreWithMultilinePlaceholders("sort_items_button.lore", placeholders)
                )
        );
    }

    public record ButtonData(Material material, String name, List<String> lore) {}

    private static final class SortButtonCacheKey {
        private final EntityLootConfig lootConfig;
        private final Material selectedMaterial;
        private final Material buttonMaterial;
        private final int hashCode;

        private SortButtonCacheKey(EntityLootConfig lootConfig, Material selectedMaterial, Material buttonMaterial) {
            this.lootConfig = lootConfig;
            this.selectedMaterial = selectedMaterial;
            this.buttonMaterial = buttonMaterial;

            int hash = System.identityHashCode(lootConfig);
            hash = 31 * hash + (selectedMaterial != null ? selectedMaterial.ordinal() : -1);
            hash = 31 * hash + buttonMaterial.ordinal();

            this.hashCode = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SortButtonCacheKey other)) return false;

            return lootConfig == other.lootConfig
                    && selectedMaterial == other.selectedMaterial
                    && buttonMaterial == other.buttonMaterial;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
