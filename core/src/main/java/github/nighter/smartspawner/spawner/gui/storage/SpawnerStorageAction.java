package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.api.events.SpawnerDropAllEvent;
import github.nighter.smartspawner.api.events.SpawnerTakeAllEvent;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.model.ItemSignature;
import github.nighter.smartspawner.spawner.model.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.bukkit.entity.Item;

import java.util.*;

import static github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI.PreviousGui.STORAGE;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerManager spawnerManager;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.messageService = plugin.getMessageService();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        // Layouts are resolved and stored per inventory session.
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) ||
                !(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();

        // Block ALL storage interactions while a sell is in progress.
        // This closes the race window where the storage GUI could be reopened (by the
        // reopenPreviousGui callback) before the async sell's item-removal step has run,
        // which would otherwise allow items to be taken from the virtual inventory twice –
        // once by the player and once by applySellResult.
        if (spawner.isSelling()) {
            event.setCancelled(true);
            plugin.getMessageService().sendMessage(player, "action_in_progress");
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }

        GuiLayout layout = holder.getLayout();

        // Player's own inventory (bottom rows, raw >= 54): allow native moves within the bag, but never
        // let items flow INTO storage. Shift-click (MOVE_TO_OTHER_INVENTORY) pushes into the top
        // inventory and a double-click gather (COLLECT_TO_CURSOR) can pull from it, so both are
        // cancelled here; every other action stays inside the player's inventory and needs no
        // reconcile because storage does not change.
        if (raw >= INVENTORY_SIZE) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        // Control buttons are fully handled by us, never natively.
        if (isControlSlot(raw, layout)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            handleControlSlotClick(
                    player, raw, holder, spawner, event.getInventory(), event.getClick(), layout);
            return;
        }

        // Item region (raw 0..44): native take-out only, no deposit.
        if (isItemSlot(raw)) {
            // Filler slots (beyond capacity on a partial last page) are display-only.
            if (raw >= SpawnerStorageUI.usableItemSlots(spawner, holder.getCurrentPage())) {
                event.setCancelled(true);
                return;
            }
            if (isTakeOutAction(event.getAction())) {
                // Let Bukkit hand the item to the player, then reconcile the count-map next tick so
                // the exact clicked cells are debited (loot that arrives meanwhile is preserved).
                scheduleReconcile(player, holder, spawner);
            } else {
                // PLACE_*, SWAP_WITH_CURSOR, HOTBAR_*, NOTHING, UNKNOWN – block anything that could
                // place an item into storage.
                event.setCancelled(true);
            }
            return;
        }

        // Unused control-row slots (raw 45..53 with no button): consume the click.
        event.setCancelled(true);
    }

    /**
     * Actions that only ever move items OUT of the clicked storage slot. Only these are allowed to run
     * natively on an item slot; everything else is a place/swap and is cancelled to enforce no-deposit.
     */
    private static boolean isTakeOutAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY, DROP_ONE_SLOT, DROP_ALL_SLOT, COLLECT_TO_CURSOR -> true;
            default -> false;
        };
    }

    /**
     * Schedules the post-native reconcile on the player's region thread for the next tick. Bukkit
     * applies the click result AFTER this LOWEST-priority listener returns, so the reconcile must run
     * a tick later to observe the moved item.
     */
    private void scheduleReconcile(Player player, StoragePageHolder holder, SpawnerData spawner) {
        Scheduler.runEntityTaskLater(player, () -> reconcileNativeTake(player, holder, spawner), 1L);
    }

    /**
     * Reconciles the count-map with the Bukkit inventory after a native take. Uses the last painted
     * image ({@code view}) as the baseline of what the player saw and acted on: for each item cell,
     * the amount that disappeared from the Bukkit slot is debited from that exact frozen cell via
     * {@link SpawnerData#takeItemFromCell}. Because that primitive clamps to the live cell amount,
     * loot that topped a cell up between render and now is kept. This is safe only under the
     * single-viewer lock, where no other Bukkit inventory of this spawner exists.
     */
    private void reconcileNativeTake(Player player, StoragePageHolder holder, SpawnerData spawner) {
        if (!player.isOnline()) {
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder current) || current != holder) {
            return;
        }

        int page = holder.getCurrentPage();
        int startSlot = (page - 1) * STORAGE_SLOTS;
        int usable = SpawnerStorageUI.usableItemSlots(spawner, page);
        StorageView view = holder.getView();

        boolean anyRemoved = false;
        for (int i = 0; i < STORAGE_SLOTS && i < usable; i++) {
            ItemStack painted = view.get(i);
            if (painted == null || painted.getType() == Material.AIR) {
                continue;
            }
            ItemStack now = inventory.getItem(i);
            int nowAmount = (now != null && now.isSimilar(painted)) ? now.getAmount() : 0;
            int removed = painted.getAmount() - nowAmount;
            if (removed > 0) {
                spawner.takeItemFromCell(startSlot + i, removed);
                anyRemoved = true;
            }
        }

        if (anyRemoved) {
            // Repaint to the current frozen cells and resync pages/capacity/hologram/menu viewers.
            // This also makes any later reconcile a no-op (painted == now), so loot is never
            // double-counted.
            updatePageAfterRemoval(player, inventory, spawner, holder);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory, org.bukkit.event.inventory.ClickType clickType, GuiLayout layout) {
        // OPTIMIZATION: Get button and action with click type fallback
        Optional<github.nighter.smartspawner.spawner.gui.layout.GuiButton> buttonOpt = layout.getButtonAtSlot(slot);
        if (buttonOpt.isEmpty()) {
            return;
        }

        var button = buttonOpt.get();
        String clickTypeString = getClickTypeString(clickType);
        String action = button.getActionWithFallback(clickTypeString);

        if (action == null || action.isEmpty()) {
            return;
        }
        if ("none".equals(action)) {
            return;
        }
        if (!plugin.getGuiButtonInteractionService().tryUse(
                player, GuiLayoutType.STORAGE_GUI, button)) {
            return;
        }

        // OPTIMIZATION: Handle actions based on action value, not button name
        switch (action) {
            case "sort_items":
                playActionResult(player, button, clickTypeString,
                        handleSortItemsClick(player, spawner, inventory));
                break;
            case "open_filter":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory);
                }
                break;
            case "take_all":
                playActionResult(player, button, clickTypeString,
                        handleTakeAllItems(player, inventory));
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory);
                }
                break;
            case "drop_page":
                playActionResult(player, button, clickTypeString,
                        handleDropPageItems(player, spawner, inventory));
                break;
            case "sell_all":
                handleSellAction(player, spawner, false, button, clickTypeString, inventory);
                break;
            case "sell_and_exp":
                handleSellAction(player, spawner, true, button, clickTypeString, inventory);
                break;
            case "collect_exp":
                playActionResult(player, button, clickTypeString,
                        handleCollectExpAction(player, spawner, inventory));
                break;
            case "return_main":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                handleReturnToMainMenu(player, spawner);
                break;
            case "none":
                // Display-only button — consume click, do nothing
                break;
            default:
                // Unknown action, log warning
                plugin.getLogger().warning("Unknown storage action: " + action);
                break;
        }
    }

    /**
     * Convert Bukkit ClickType to string for action lookup
     * OPTIMIZATION: Cached string values to avoid repeated string creation
     */
    private String getClickTypeString(org.bukkit.event.inventory.ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left_click";
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "left_click";
        };
    }

    /**
     * Handle sell action with optional exp collection
     * OPTIMIZATION: Extracted common sell logic to reduce code duplication
     */
    private void handleSellAction(Player player, SpawnerData spawner, boolean collectExp,
                                  github.nighter.smartspawner.spawner.gui.layout.GuiButton sourceButton,
                                  String sourceClickType, Inventory inventory) {
        if (!plugin.hasSellIntegration()) {
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        // Check if there are items to sell
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            if (collectExp && spawner.getSpawnerExp() > 0) {
                // No items to sell, but collect the stored exp without leaving the storage GUI
                boolean success = handleCollectExpAction(player, spawner, inventory);
                playActionResult(player, sourceButton, sourceClickType, success);
            } else {
                messageService.sendMessage(player, "spawner_storage_empty");
                plugin.getGuiButtonInteractionService().playFailSound(
                        player, sourceButton, sourceClickType);
            }
            return;
        }

        // Open confirmation GUI
        plugin.getSpawnerSellConfirmUI().openSellConfirmGui(
                player, spawner, STORAGE, collectExp, sourceButton, sourceClickType);
    }

    /**
     * Collects stored XP from the spawner while keeping the player on the storage GUI.
     */
    private boolean handleCollectExpAction(Player player, SpawnerData spawner, Inventory inventory) {
        boolean collected = plugin.getSpawnerMenuAction().tryCollectExpForPlayer(player, spawner);
        if (collected) {
            // Refresh button display so the XP counter updates to 0
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder != null) {
                plugin.getSpawnerStorageUI().updateDisplay(inventory, spawner, holder.getCurrentPage(), holder.getTotalPages());
            }
        }
        return collected;
    }

    /**
     * Handle return to main menu action
     */
    private void handleReturnToMainMenu(Player player, SpawnerData spawner) {
        player.closeInventory();
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private boolean isControlSlot(int slot, GuiLayout layout) {
        return layout != null && layout.isSlotUsed(slot);
    }

    private boolean isItemSlot(int slot) {
        // First 45 slots (0-44) are for storage items
        return slot >= 0 && slot < STORAGE_SLOTS;
    }

    /**
     * Places {@code amount} of {@code template} into the player's main inventory, stacking into
     * matching partial stacks first, then empty slots. The caller (take-all / drop) must have verified
     * the amount fits (e.g. via {@link #simulateBagFill}), so nothing is dropped.
     */
    private void addToPlayerInventory(PlayerInventory playerInv, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot != null && slot.getType() != Material.AIR && slot.isSimilar(template)) {
                int room = maxStack - slot.getAmount();
                if (room > 0) {
                    int add = Math.min(room, remaining);
                    slot.setAmount(slot.getAmount() + add);
                    remaining -= add;
                }
            }
        }

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int add = Math.min(remaining, maxStack);
                ItemStack newStack = template.clone();
                newStack.setAmount(add);
                playerInv.setItem(i, newStack);
                remaining -= add;
            }
        }
    }

    /**
     * Simulates filling the player's bag with the given signatures (in iteration order) without
     * mutating the real inventory. Returns how much of each signature would fit, competing for
     * the same empty slots as a real fill would. Used to size a take-all before committing it.
     */
    private Map<ItemSignature, Long> simulateBagFill(PlayerInventory playerInv,
                                                     Collection<Map.Entry<ItemSignature, Long>> ordered) {
        ItemStack[] slots = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = playerInv.getItem(i);
            slots[i] = (s == null || s.getType() == Material.AIR) ? null : s.clone();
        }

        Map<ItemSignature, Long> acceptable = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : ordered) {
            ItemSignature signature = entry.getKey();
            long avail = entry.getValue() == null ? 0L : entry.getValue();
            if (signature == null || avail <= 0) {
                continue;
            }

            ItemStack template = signature.getTemplate();
            int maxStack = template.getMaxStackSize();
            long placed = 0;

            for (int i = 0; i < 36 && placed < avail; i++) {
                ItemStack slot = slots[i];
                if (slot != null && slot.isSimilar(template)) {
                    int room = maxStack - slot.getAmount();
                    if (room > 0) {
                        int add = (int) Math.min(room, avail - placed);
                        slot.setAmount(slot.getAmount() + add);
                        placed += add;
                    }
                }
            }
            for (int i = 0; i < 36 && placed < avail; i++) {
                if (slots[i] == null) {
                    int add = (int) Math.min(maxStack, avail - placed);
                    ItemStack ns = template.clone();
                    ns.setAmount(add);
                    slots[i] = ns;
                    placed += add;
                }
            }

            if (placed > 0) {
                acceptable.put(signature, placed);
            }
        }
        return acceptable;
    }

    /** Splits a signature-to-amount map into displayable stacks keyed by sequential slot index. */
    private Map<Integer, ItemStack> projectToSlots(Map<ItemSignature, Long> items) {
        Map<Integer, ItemStack> out = new HashMap<>();
        int slot = 0;
        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                out.put(slot++, stack);
                remaining -= amt;
            }
        }
        return out;
    }

    /** Consolidates a slot-keyed ItemStack map back into signature-to-amount. */
    private Map<ItemSignature, Long> consolidateSlots(Map<Integer, ItemStack> slots) {
        Map<ItemSignature, Long> out = new HashMap<>();
        for (ItemStack item : slots.values()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            out.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        return out;
    }


    /**
     * Updates the page display after items are removed from storage.
     */
    private void updatePageAfterRemoval(Player player, Inventory inventory,
                                       SpawnerData spawner, StoragePageHolder holder) {
        // Recalculate pages
        int newTotalPages = calculateTotalPages(spawner);
        int currentPage = holder.getCurrentPage();

        // Clamp to valid page range
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
        }
        holder.updateOldUsedSlots();

        // Update display
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        spawnerStorageUI.updateDisplay(inventory, spawner, adjustedPage, newTotalPages);

        // Update title if pages changed
        if (newTotalPages != currentPage || adjustedPage != currentPage) {
            updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);
        }

        // Update hologram and other viewers
        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Check capacity against real occupancy (packed), not the display layout which can carry holes.
        if (spawner.getMaxSpawnerLootSlots() > spawner.getVirtualInventory().getUsedSlots()
                && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }

        // Mark as modified
        spawner.markStorageDirty();
    }

    private boolean handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return false;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int startSlot = (holder.getCurrentPage() - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Project the current page from the source of truth (count-map), not the GUI slots.
        Int2ObjectMap<ItemStack> pageDisplay =
                virtualInv.getDisplayPage(holder.getCurrentPage(), StoragePageHolder.MAX_ITEMS_PER_PAGE);
        if (pageDisplay.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> pageItems = new ArrayList<>(pageDisplay.values());
        boolean hasDropListeners = SpawnerDropAllEvent.getHandlerList().getRegisteredListeners().length != 0;

        if (hasDropListeners) {
            SpawnerDropAllEvent event = new SpawnerDropAllEvent(player, spawner.getSpawnerLocation(), pageItems);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            pageItems = event.getItems();
        }

        // Atomic removal; drop back exactly what was removed (dupe-safe against stale views).
        // While frozen with no addon rewriting the list, empty the page's exact cells so the acted
        // page clears in place instead of pulling items up from later pages. Otherwise fall back to a
        // by-signature take built from the (possibly addon-modified) projected list.
        Map<ItemSignature, Long> removed;
        if (!hasDropListeners && virtualInv.isOrderFrozen()) {
            removed = spawner.takeItemsFromCellRange(startSlot, StoragePageHolder.MAX_ITEMS_PER_PAGE);
        } else {
            Map<ItemSignature, Long> desired = new HashMap<>();
            for (ItemStack item : pageItems) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                    continue;
                }
                desired.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
            }
            if (desired.isEmpty()) {
                messageService.sendMessage(player, "spawner_storage_empty");
                return false;
            }
            removed = spawner.takeItems(desired);
        }
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> toDrop = new ArrayList<>();
        long itemsFoundCount = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            itemsFoundCount += remaining;
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                toDrop.add(stack);
                remaining -= amt;
            }
        }

        final long itemsFound = itemsFoundCount;

        dropItemsInDirection(player, toDrop);

        int newTotalPages = calculateTotalPages(spawner);
        if (holder.getCurrentPage() > newTotalPages) {
            holder.setCurrentPage(Math.max(1, newTotalPages));
        }
        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();

        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Capacity is about real occupancy (packed), not the display layout which can carry holes.
        if (spawner.getMaxSpawnerLootSlots() > spawner.getVirtualInventory().getUsedSlots()
                && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }
        spawner.markStorageDirty();

        // Log drop page items action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_dropped", itemsFound)
                            .metadata("page_number", holder.getCurrentPage())
            );
        }

        updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        return true;
    }

    private void dropItemsInDirection(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);

        Vector velocity = new Vector(
                sinYaw * cosPitch * 0.3,
                sinPitch * 0.3 + 0.1,
                cosYaw * cosPitch * 0.3
        );

        for (ItemStack item : items) {
            Item droppedItem = world.dropItem(dropLocation, item, drop -> {
                drop.setThrower(playerUUID);
                drop.setPickupDelay(40);
            });


            droppedItem.setVelocity(velocity);
        }
    }


    private void openFilterConfig(Player player, SpawnerData spawner) {
        filterConfigUI.openFilterConfigGUI(player, spawner);
    }


    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        spawnerStorageUI.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, spawner, newPage, totalPages);

    }

    private int calculateTotalPages(SpawnerData spawner) {
        // Pages follow the display layout (frozen cells, holes included), not the packed item count,
        // so navigation matches what is actually rendered. Must agree with
        // SpawnerStorageUI.calculateTotalPages, which uses the same source.
        int displaySlots = spawner.getVirtualInventory().getDisplaySlotCount();
        return Math.max(1, (int) Math.ceil((double) displaySlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, SpawnerData spawner, int page, int totalPages) {
        String newTitle = plugin.getSpawnerStorageUI().getStorageTitle(spawner, page, totalPages);

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page);
        }
    }

    private boolean handleSortItemsClick(Player player, SpawnerData spawner, Inventory inventory) {
        // Validate loot config
        if (spawner.getLootConfig() == null || spawner.getLootConfig().getAllItems() == null) {
            return false;
        }

        var lootItems = spawner.getLootConfig().getAllItems();
        if (lootItems.isEmpty()) {
            return false;
        }

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Build sorted list of available materials
        var sortedLoot = lootItems.stream()
                .map(LootItem::material)
                .distinct() // Remove duplicates if any
                .sorted(Comparator.comparing(Material::name))
                .toList();

        if (sortedLoot.isEmpty()) {
            return false;
        }

        // Find next sort item
        Material nextSort;

        if (currentSort == null) {
            // No current sort, select first item
            nextSort = sortedLoot.getFirst();
        } else {
            // Find current item index
            int currentIndex = sortedLoot.indexOf(currentSort);

            if (currentIndex == -1) {
                // Current sort item not in list anymore, reset to first
                nextSort = sortedLoot.getFirst();
            } else {
                // Select next item (wrap around to first if at end)
                int nextIndex = (currentIndex + 1) % sortedLoot.size();
                nextSort = sortedLoot.get(nextIndex);
            }
        }

        // Apply the new sort preference: re-sorts and re-pins the frozen order, and bumps the storage
        // version so other viewers redraw in the new order.
        spawner.applySortPreference(nextSort);

        // Mark spawner as modified to save the preference
        spawner.markStorageDirty();
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

        // Update GUI display to reflect VirtualInventory state
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        }

        // Log items sort action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEMS_SORT, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("sort_item", nextSort.name())
                            .metadata("previous_sort", currentSort != null ? currentSort.name() : "none")
            );
        }
        return true;
    }

    private void openLootPage(Player player, SpawnerData spawner, int page) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        int totalPages = calculateTotalPages(spawner);
        final int finalPage = Math.max(1, Math.min(page, totalPages));
        Inventory pageInventory = spawnerStorageUI.createStorageInventory(player, spawner, finalPage, totalPages);

        // Log storage GUI opening
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_STORAGE_OPEN, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("page", finalPage)
                            .metadata("total_pages", totalPages)
            );
        }

        player.openInventory(pageInventory);
    }

    public boolean handleTakeAllItems(Player player, Inventory sourceInventory) {
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        PlayerInventory playerInv = player.getInventory();

        int currentPage = holder.getCurrentPage();
        int startSlot = (currentPage - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Scope to the page the player is actually viewing (like drop-page), projected from the
        // count-map rather than the GUI slots. An empty page takes nothing, even if later pages hold items.
        Int2ObjectMap<ItemStack> pageDisplay =
                virtualInv.getDisplayPage(currentPage, StoragePageHolder.MAX_ITEMS_PER_PAGE);
        if (pageDisplay.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // Consolidate this page's cells by signature: what the current page holds.
        Map<ItemSignature, Long> pageAvailable = new HashMap<>();
        for (ItemStack item : pageDisplay.values()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            pageAvailable.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        if (pageAvailable.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // How much of THIS page the bag can accept, competing for the same slots as a real fill.
        Map<ItemSignature, Long> desired = simulateBagFill(playerInv, pageAvailable.entrySet());
        if (desired.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        boolean hasTakeListeners = SpawnerTakeAllEvent.getHandlerList().getRegisteredListeners().length != 0;
        if (hasTakeListeners) {
            Map<Integer, ItemStack> projected = projectToSlots(desired);
            SpawnerTakeAllEvent event = new SpawnerTakeAllEvent(player, spawner.getSpawnerLocation(), projected);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            desired = consolidateSlots(event.getItems());
            if (desired.isEmpty()) return false;
        }

        // Atomic removal; place back exactly what was removed (dupe-safe against stale views).
        // While frozen with no addon rewriting the list, empty this page's exact cells (capped by bag
        // space) so the page clears in place instead of pulling items up from later pages. Otherwise
        // fall back to a by-signature take built from the (possibly addon-modified) desired amounts.
        Map<ItemSignature, Long> removed;
        if (!hasTakeListeners && virtualInv.isOrderFrozen()) {
            removed = takeFromPageCells(spawner, pageDisplay, startSlot, desired);
        } else {
            removed = spawner.takeItems(desired);
        }
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        long totalMoved = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            addToPlayerInventory(playerInv, entry.getKey().getTemplate(), entry.getValue().intValue());
            totalMoved += entry.getValue();
        }
        final long totalMovedFinal = totalMoved;
        spawner.updateHologramData();
        player.updateInventory();

        int newTotalPages = calculateTotalPages(spawner);

        // Clamp current page to valid range (e.g., if on page 6 but only 5 pages remain)
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
            SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
            spawnerStorageUI.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        } else {
            // Same page: still repaint so the emptied slots clear.
            plugin.getSpawnerStorageUI().updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        }

        updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);

        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Capacity is about real occupancy (packed), not the display layout which can carry holes.
        if (spawner.getMaxSpawnerLootSlots() > spawner.getVirtualInventory().getUsedSlots()
                && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }
        spawner.markStorageDirty();

        // Log take all items action
        if (plugin.getSpawnerActionLogger() != null) {
            int itemsLeft = spawner.getVirtualInventory().getUsedSlots();
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_TAKE_ALL, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_taken", totalMovedFinal)
                            .metadata("items_left", itemsLeft)
            );
        }
        return true;
    }

    /**
     * Capacity-aware page take: removes from the exact display cells of the acted page, in slot order,
     * up to the per-signature amounts the bag accepted. Mirrors drop-page's frozen behaviour (cells
     * empty in place) but stops once each signature's bag budget is exhausted. Requires a frozen order.
     *
     * @param pageDisplay page cells keyed by page-relative slot (from {@code getDisplayPage})
     * @param startSlot   global display slot the page starts at ({@code (page - 1) * MAX_ITEMS_PER_PAGE})
     * @param desired     per-signature cap on how much to take (what the bag can accept)
     * @return signature to amount actually removed
     */
    private Map<ItemSignature, Long> takeFromPageCells(
            SpawnerData spawner, Int2ObjectMap<ItemStack> pageDisplay, int startSlot,
            Map<ItemSignature, Long> desired) {
        Map<ItemSignature, Long> removed = new HashMap<>();
        Map<ItemSignature, Long> budget = new HashMap<>(desired);
        for (int relativeSlot = 0; relativeSlot < StoragePageHolder.MAX_ITEMS_PER_PAGE; relativeSlot++) {
            ItemStack cell = pageDisplay.get(relativeSlot);
            if (cell == null || cell.getType() == Material.AIR || cell.getAmount() <= 0) {
                continue;
            }
            ItemSignature sig = VirtualInventory.getSignature(cell);
            long left = budget.getOrDefault(sig, 0L);
            if (left <= 0) {
                continue;
            }
            long want = Math.min(left, cell.getAmount());
            Map<ItemSignature, Long> cellRemoved = spawner.takeItemFromCell(startSlot + relativeSlot, want);
            long got = cellRemoved.getOrDefault(sig, 0L);
            if (got > 0) {
                removed.merge(sig, got, Long::sum);
                budget.merge(sig, -got, Long::sum);
            }
        }
        return removed;
    }

    private void playActionResult(
            Player player,
            github.nighter.smartspawner.spawner.gui.layout.GuiButton button,
            String clickType,
            boolean success) {
        if (success) {
            plugin.getGuiButtonInteractionService().playSuccessSound(player, button, clickType);
        } else {
            plugin.getGuiButtonInteractionService().playFailSound(player, button, clickType);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder)) {
            return;
        }
        // A drag can only ever PLACE items, so any drag touching the top (storage) inventory is a
        // deposit and is cancelled. A drag confined to the player's own inventory is allowed.
        for (int raw : event.getRawSlots()) {
            if (raw < INVENTORY_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        // Inventory close events already execute on the owning player's region thread.
        // Do not defer this work to the player's scheduler: after closing, a block-backed
        // inventory could belong to a different region and resolving its holder there
        // violates Folia's thread ownership rules.
        handleInventoryClose(holder);
    }

    private void handleInventoryClose(StoragePageHolder holder) {
        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isStorageDirty()){
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            spawner.clearStorageDirty();
        }
    }
}
