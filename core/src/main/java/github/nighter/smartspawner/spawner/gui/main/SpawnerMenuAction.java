package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExpClaimEvent;
import github.nighter.smartspawner.hooks.rpg.AuraSkillsIntegration;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerMenuAction implements Listener {
    private static final Set<Material> SPAWNER_INFO_MATERIALS = Set.of(
        Material.PLAYER_HEAD,
        Material.SPAWNER,
        Material.ZOMBIE_HEAD,
        Material.SKELETON_SKULL,
        Material.WITHER_SKELETON_SKULL,
        Material.CREEPER_HEAD,
        Material.PIGLIN_HEAD,
        Material.DRAGON_HEAD
    );
    private final SmartSpawner plugin;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStackerUI spawnerStackerUI;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private AuraSkillsIntegration auraSkills;

    // Anti spam click properties
    private final Map<UUID, Long> lastInfoClickTime = new ConcurrentHashMap<>();

    public SpawnerMenuAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerUI = plugin.getSpawnerStackerUI();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.auraSkills = plugin.getIntegrationManager().getAuraSkillsIntegration();
    }

    public void reload() {
        this.auraSkills = plugin.getIntegrationManager().getAuraSkillsIntegration();
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder(false) instanceof SpawnerMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        SpawnerData spawner = holder.getSpawnerData();

        // Verify click was in the actual menu and not player inventory
        if (event.getClickedInventory() == null ||
                !(event.getClickedInventory().getHolder(false) instanceof SpawnerMenuHolder)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Use layout-based action handling
        int slot = event.getRawSlot();
        String clickType = getClickTypeString(event.getClick());
        
        if (handleLayoutAction(player, spawner, slot, clickType)) {
            return;
        }

        // Fallback to legacy material-based handling for backward compatibility
        Material itemType = clickedItem.getType();
        if (itemType == Material.CHEST) {
            handleStorageClick(player, spawner);
        } else if (SPAWNER_INFO_MATERIALS.contains(itemType)) {
            handleSpawnerInfoClick(player, spawner, event.getClick());
        } else if (itemType == Material.EXPERIENCE_BOTTLE) {
            handleExpBottleClick(player, spawner, false);
        }
    }

    private boolean handleLayoutAction(Player player, SpawnerData spawner, int slot, String clickType) {
        var layoutConfig = plugin.getGuiLayoutConfig();
        var layout = layoutConfig.getCurrentMainLayout();
        
        if (layout == null) {
            return false;
        }

        var buttonOpt = layout.getButtonAtSlot(slot);
        if (buttonOpt.isEmpty()) {
            return false;
        }

        var button = buttonOpt.get();
        String action = button.getActionWithFallback(clickType);

        if (action == null || action.isEmpty()) {
            // Button exists but no action for this click type
            // Consume the click to prevent legacy material-based fallback from firing
            return true;
        }

        if (action.equals("none")) {
            return true; // Explicitly disabled action — consume click, do nothing
        }

        switch (action) {
            case "open_storage":
                handleStorageClick(player, spawner);
                return true;
            case "open_stacker":
                if (isClickTooFrequent(player)) {
                    return true;
                }
                // Check stacker permission and open stacker GUI
                if (!player.hasPermission("smartspawner.stack")) {
                    messageService.sendMessage(player, "no_permission");
                    return true;
                }
                spawnerStackerUI.openStackerGui(player, spawner);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return true;
            case "sell_and_exp":
                if (isClickTooFrequent(player)) {
                    return true;
                }
                // Check permissions for selling (same logic as handleSpawnerInfoClick)
                if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
                    messageService.sendMessage(player, "no_permission");
                    return true;
                }
                // If no items to sell, still allow exp collection
                // Pass isSell=true to bypass the inner cooldown check (already checked above)
                if (spawner.getVirtualInventory().getUsedSlots() == 0) {
                    handleExpBottleClick(player, spawner, true);
                    return true;
                }
                // Open confirmation GUI with exp collection enabled
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getSpawnerSellConfirmUI().openSellConfirmGui(player, spawner,
                    github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI.PreviousGui.MAIN_MENU, true);
                return true;
            case "sell_all":
                if (isClickTooFrequent(player)) {
                    return true;
                }
                // Check permissions for selling
                if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
                    messageService.sendMessage(player, "no_permission");
                    return true;
                }
                // Sell all items only (no XP collection)
                handleSellAllItems(player, spawner);
                return true;
            case "collect_exp":
                handleExpBottleClick(player, spawner, false);
                return true;
            default:
                return false;
        }
    }

    private String getClickTypeString(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left_click";
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "left_click";
        };
    }

    public void handleStorageClick(Player player, SpawnerData spawner) {
        Inventory pageInventory = spawnerStorageUI.createStorageInventory(spawner, 1, -1);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.openInventory(pageInventory);
    }

    private void handleSpawnerInfoClick(Player player, SpawnerData spawner, ClickType clickType) {
        if (isClickTooFrequent(player)) {
            return;
        }

        // Determine which mode we're in based on shop integration
        boolean hasShopIntegration = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");

        // Handle clicks based on shop integration mode
        if (hasShopIntegration) {
            // Standard mode: Left click for selling/XP, right click for stacker
            if (clickType == ClickType.LEFT) {
                // Collect EXP and sell items in storage
                handleExpBottleClick(player, spawner, true);
                handleSellAllItems(player, spawner);
            } else if (clickType == ClickType.RIGHT) {
                // Check stacker permission
                if (!player.hasPermission("smartspawner.stack")) {
                    messageService.sendMessage(player, "no_permission");
                    return;
                }

                // Open stacker GUI
                spawnerStackerUI.openStackerGui(player, spawner);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        } else {
            // Check stacker permission
            if (!player.hasPermission("smartspawner.stack")) {
                messageService.sendMessage(player, "no_permission");
                return;
            }

            // Open stacker GUI
            spawnerStackerUI.openStackerGui(player, spawner);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastInfoClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastInfoClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300; // 300ms threshold
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastInfoClickTime.remove(event.getPlayer().getUniqueId());
    }

    private void handleSellAllItems(Player player, SpawnerData spawner) {
        if (!plugin.hasSellIntegration()) return;

        // Permission check
        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Check if there are items to sell
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return;
        }

        // Open confirmation GUI - from main menu, no exp collection
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        plugin.getSpawnerSellConfirmUI().openSellConfirmGui(player, spawner,
            github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI.PreviousGui.MAIN_MENU, false);
    }

    /**
     * Collects XP from a spawner without navigating the player to any GUI.
     * Used by the storage GUI so the player stays on the storage page after collecting.
     * Returns {@code true} if XP was successfully collected.
     */
    public boolean collectExpForPlayer(Player player, SpawnerData spawner) {
        long exp = spawner.getSpawnerExp();
        if (exp <= 0) {
            messageService.sendMessage(player, "no_exp");
            return false;
        }

        long initialExp = exp;
        long expUsedForMending = 0;

        if (plugin.getConfig().getBoolean("spawner_properties.default.allow_exp_mending")) {
            expUsedForMending = applyMendingFromExp(player, exp);
            exp -= expUsedForMending;
        }

        if (auraSkills != null) {
            giveAuraSkillsXp(player, spawner, initialExp);
        }

        if (exp > 0) {
            if (SpawnerExpClaimEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerExpClaimEvent expClaimEvent = new SpawnerExpClaimEvent(player, spawner.getSpawnerLocation(), exp);
                Bukkit.getPluginManager().callEvent(expClaimEvent);
                if (expClaimEvent.isCancelled()) return false;
                if (exp != expClaimEvent.getExpAmount()) exp = expClaimEvent.getExpAmount();
            }
            givePlayerExpInChunks(player, exp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        spawner.setSpawnerExp(0);
        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        if (spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
            if (spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }

        sendExpCollectionMessage(player, initialExp, expUsedForMending);
        return true;
    }

    public void handleExpBottleClick(Player player, SpawnerData spawner, boolean isSell) {
        if (isClickTooFrequent(player) && !isSell) {
            return;
        }

        long exp = spawner.getSpawnerExp();

        if (exp <= 0 && !isSell) {
            messageService.sendMessage(player, "no_exp");
            return;

        }

        long initialExp = exp;
        long expUsedForMending = 0;

        // Apply mending first if enabled
        if (plugin.getConfig().getBoolean("spawner_properties.default.allow_exp_mending")) {
            expUsedForMending = applyMendingFromExp(player, exp);
            exp -= expUsedForMending;
        }

        // Give AuraSkills XP if integration is enabled
        if (auraSkills != null) {
            giveAuraSkillsXp(player, spawner, initialExp);
        }

        // Give remaining exp to player
        if (exp > 0) {
            if(SpawnerExpClaimEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerExpClaimEvent expClaimEvent = new SpawnerExpClaimEvent(player, spawner.getSpawnerLocation(), exp);
                Bukkit.getPluginManager().callEvent(expClaimEvent);
                if(expClaimEvent.isCancelled()) return;
                if(exp != expClaimEvent.getExpAmount()) exp = expClaimEvent.getExpAmount();
            }
            givePlayerExpInChunks(player, exp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        // Reset spawner exp and update menu
        spawner.setSpawnerExp(0);
        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
        
        // Check if player is Bedrock and use appropriate menu
        if (isBedrockPlayer(player)) {
            if (plugin.getSpawnerMenuFormUI() != null) {
                plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
            } else {
                // Fallback to standard GUI if FormUI not available
                spawnerMenuUI.openSpawnerMenu(player, spawner, true);
            }
        } else {
            spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        }

        // Update all viewers instead of just current player
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Update spawner capacity status
        if (spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
            if (spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }

        // Send appropriate message based on exp distribution
        sendExpCollectionMessage(player, initialExp, expUsedForMending);
    }

    private long applyMendingFromExp(Player player, long availableExp) {
        if (availableExp <= 0) {
            return 0;
        }

        long expUsed = 0;
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> itemsToCheck = Arrays.asList(
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        );

        for (ItemStack item : itemsToCheck) {
            if (availableExp <= 0) {
                break;
            }

            if (item == null || item.getType() == Material.AIR ||
                    !item.getEnchantments().containsKey(Enchantment.MENDING)) {
                continue;
            }

            if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }

            // Calculate repair amount based on available exp
            int damage = damageable.getDamage();
            long durabilityToRepair = Math.min(damage, availableExp * 2);
            long expNeeded = (durabilityToRepair + 1) / 2; // Round up for partial repairs

            if (expNeeded <= 0) {
                continue;
            }

            // Apply repair and track exp usage
            long actualExpUsed = Math.min(expNeeded, availableExp);
            long actualRepair = actualExpUsed * 2;

            // Ensure damage value does not go negative
            int newDamage = (int) Math.max(0L, damage - actualRepair);

            Damageable meta = (Damageable) item.getItemMeta();
            meta.setDamage(newDamage);
            item.setItemMeta(meta);

            availableExp -= actualExpUsed;
            expUsed += actualExpUsed;

            // Visual and sound effects for mending
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 5);
        }

        return expUsed;
    }

    private void sendExpCollectionMessage(Player player, long totalExp, long mendingExp) {
        Map<String, String> placeholders = new HashMap<>();

        if (mendingExp > 0) {
            long remainingExp = totalExp - mendingExp;
            placeholders.put("exp_mending", languageManager.formatNumber(mendingExp));
            placeholders.put("exp", languageManager.formatNumber(remainingExp));
            messageService.sendMessage(player, "exp_collected_with_mending", placeholders);
        } else {
            if (totalExp > 0) {
                placeholders.put("exp", plugin.getLanguageManager().formatNumber(totalExp));
                messageService.sendMessage(player, "exp_collected", placeholders);
            }
        }
    }

    private void giveAuraSkillsXp(Player player, SpawnerData spawner, long totalExp) {
        try {
            if (auraSkills == null || !auraSkills.isEnabled()) {
                return;
            }

            // Get the entity type from the spawner
            EntityType entityType = spawner.getEntityType();
            if (entityType == null) {
                plugin.debug("Could not determine entity type for spawner at " + spawner.getSpawnerLocation());
                return;
            }

            // Give skill XP based on the entity type and exp amount
            auraSkills.giveSkillXp(player, entityType, clampLongToInt(totalExp));

        } catch (Exception e) {
            plugin.getLogger().warning("Error giving AuraSkills XP: " + e.getMessage());
            plugin.debug("AuraSkills integration error: " + e.toString());
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    private void givePlayerExpInChunks(Player player, long totalExp) {
        long remaining = totalExp;
        while (remaining > 0) {
            int grant = clampLongToInt(Math.min(remaining, Integer.MAX_VALUE));
            player.giveExp(grant);
            remaining -= grant;
        }
    }

    private int clampLongToInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
