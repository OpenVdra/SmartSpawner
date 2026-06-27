package github.nighter.smartspawner.spawner.gui.storage.filter;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FilterConfigUI implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final Set<Integer> DIVIDER_SLOTS = Set.of(4, 13, 22);

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerStorageUI storageUI;

    // Precomputed buttons for better performance
    private final Map<String, ItemStack> staticButtons;

    // Anti spam click properties
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();

    public FilterConfigUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.storageUI = plugin.getSpawnerStorageUI();
        this.staticButtons = new HashMap<>();

        initializeStaticButtons();

        // Register this class as an event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        // Clear existing static buttons
        staticButtons.clear();
        // Reinitialize static buttons
        initializeStaticButtons();
    }

    private void initializeStaticButtons() {
        // Create divider button that also functions as return button
        staticButtons.put("divider", createButton(
                Material.CYAN_STAINED_GLASS_PANE,
                languageManager.getGuiItemName("filter_divider.name"),
                languageManager.getGuiItemLoreAsList("filter_divider.lore")
        ));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastItemClickTime.put(player.getUniqueId(), now);
        return (now - last) < 200;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
    }

    /**
     * Opens the filter configuration GUI for a player and spawner
     */
    public void openFilterConfigGUI(Player player, SpawnerData spawner) {
        // Create a new inventory with title from language manager
        String title = languageManager.getGuiTitle("gui_title_filter_config");
        Inventory filterInventory = Bukkit.createInventory(
                new FilterConfigHolder(spawner),
                INVENTORY_SIZE,
                title);

        // Setup the filter inventory
        setupFilterInventory(filterInventory, spawner);

        player.openInventory(filterInventory);
    }

    /**
     * Sets up the filter inventory content
     */
    private void setupFilterInventory(Inventory inventory, SpawnerData spawner) {
        // Clear the inventory first to prevent duplication bugs
        inventory.clear();

        // Get all available loot items
        List<LootItem> allLootItems;
        if (spawner.getLootConfig() == null) {
            allLootItems = Collections.emptyList();
        } else {
            allLootItems = spawner.getLootConfig().getAllItems();
        }

        // Get currently filtered items for quick lookup
        Set<Material> filteredItems = spawner.getFilteredItems();

        // Set divider elements (which also act as return buttons)
        ItemStack divider = staticButtons.get("divider");
        for (Integer slot : DIVIDER_SLOTS) {
            inventory.setItem(slot, divider);
        }

        // Separate items into allowed and filtered lists
        List<ItemStack> allowedItems = new ArrayList<>();
        List<ItemStack> blockedItems = new ArrayList<>();

        // Categorize items based on filter status
        for (LootItem lootItem : allLootItems) {
            ItemStack displayItem = lootItem.createItemStack();
            if (displayItem == null) continue;

            Material itemType = displayItem.getType();
            if (filteredItems.contains(itemType)) {
                displayItem = addFilterMarkerToItem(displayItem, true);
                blockedItems.add(displayItem);
            } else {
                displayItem = addFilterMarkerToItem(displayItem, false);
                allowedItems.add(displayItem);
            }
        }

        // Place allowed items on the left side (columns 0-3)
        placeItemsInSection(inventory, allowedItems, 0, 4);

        // Place filtered items on the right side (columns 5-8)
        placeItemsInSection(inventory, blockedItems, 5, 4);
    }

    /**
     * Adds appropriate lore to items based on their filter status
     */
    private ItemStack addFilterMarkerToItem(ItemStack item, boolean isFiltered) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String itemName = languageManager.getVanillaItemName(item.getType());

            // Set the display name with correct formatting based on status
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item_name", itemName);

            String nameKey = isFiltered ? "filter_item_blocked.name" : "filter_item_allowed.name";
            meta.setDisplayName(languageManager.getGuiItemName(nameKey, placeholders));

            // Set the lore with correct status information
            String loreKey = isFiltered ? "filter_item_blocked.lore" : "filter_item_allowed.lore";
            meta.setLore(languageManager.getGuiItemLoreAsList(loreKey, placeholders));

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Places items in their section of the inventory
     */
    private void placeItemsInSection(Inventory inventory, List<ItemStack> items, int startColumn, int columnsSpan) {
        int index = 0;
        for (ItemStack item : items) {
            // Calculate position
            int row = index / columnsSpan;
            int column = startColumn + (index % columnsSpan);
            int slot = row * 9 + column;

            // Only place if within inventory bounds and not in divider column
            if (slot < INVENTORY_SIZE && !DIVIDER_SLOTS.contains(slot)) {
                inventory.setItem(slot, item);
            }
            index++;
        }
    }

    /**
     * Handles clicks in the filter inventory
     */
    @EventHandler
    public void onFilterInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof FilterConfigHolder holder)) {
            return;
        }

        // Cancel default interaction
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        
        // Validate spawner still exists - prevent exploits on broken spawners
        if (!isSpawnerValid(spawner)) {
            player.closeInventory();
            return;
        }

        if (isClickTooFrequent(player)) {
            return;
        }

        int slot = event.getRawSlot();

        // Handle divider clicks (return to storage)
        if (DIVIDER_SLOTS.contains(slot)) {
            returnToStorage(player, spawner);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Toggle item filter status
        boolean wasUpdated = toggleItemFilter(player, spawner, clickedItem);

        if (wasUpdated) {
            // Refresh the inventory display immediately
            setupFilterInventory(event.getInventory(), spawner);
        }
    }

    /**
     * Validates that a spawner still exists in the manager.
     * Prevents exploits when spawner is broken while GUI is open.
     *
     * @param spawner The spawner to validate
     * @return true if spawner is valid, false otherwise
     */
    private boolean isSpawnerValid(SpawnerData spawner) {
        if (spawner == null) {
            return false;
        }
        
        var spawnerManager = plugin.getSpawnerManager();
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        return current != null && current == spawner;
    }

    /**
     * Returns to the spawner storage UI
     */
    private void returnToStorage(Player player, SpawnerData spawner) {
        // Return to storage menu
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        player.closeInventory();

        // Open storage UI (page 1 with no specific slot focus)
        Inventory pageInventory = storageUI.createStorageInventory(player, spawner, 1, -1);
        player.openInventory(pageInventory);
    }

    /**
     * Toggles whether an item is filtered
     * @return true if the filter was updated, false otherwise
     */
    private boolean toggleItemFilter(Player player, SpawnerData spawner, ItemStack clickedItem) {
        Material itemType = clickedItem.getType();

        // Toggle filter status
        boolean wasFiltered = spawner.toggleItemFilter(itemType);

        // Play sound based on action result
        Sound sound = wasFiltered ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.UI_BUTTON_CLICK;
        player.playSound(player.getLocation(), sound, 0.5f, 1.0f);

        // Log item filter action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_FILTER, builder -> 
                builder.player(player.getName(), player.getUniqueId())
                    .location(spawner.getSpawnerLocation())
                    .entityType(spawner.getEntityType())
                    .metadata("item_type", itemType.name())
                    .metadata("is_filtered", wasFiltered)
            );
        }

        return true;
    }
}
