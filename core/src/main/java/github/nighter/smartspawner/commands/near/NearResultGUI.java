package github.nighter.smartspawner.commands.near;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Displays the spawners found by /ss near in a paged GUI,
 * similar to the SpawnerListGUI but scoped to the session's scan results.
 */
public class NearResultGUI implements Listener {

    private static final int SPAWNERS_PER_PAGE = 45;
    private static final Pattern ID_PATTERN = Pattern.compile("#([A-Za-z0-9]+)");
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerHighlightManager highlightManager;
    private final SpawnerManagementGUI spawnerManagementGUI;

    public NearResultGUI(SmartSpawner plugin, SpawnerHighlightManager highlightManager) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.highlightManager = highlightManager;
        this.spawnerManagementGUI = new SpawnerManagementGUI(plugin);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Open the near-result GUI at the given page.
     * Fetches the spawner list from the player's active session.
     */
    public void openNearResultGUI(Player player, int page) {
        List<SpawnerData> spawners = highlightManager.getSessionSpawners(player.getUniqueId());
        if (spawners.isEmpty()) {
            plugin.getMessageService().sendMessage(player, "near.no_active_scan");
            return;
        }
        openPage(player, spawners, page);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  GUI building
    // ──────────────────────────────────────────────────────────────────────────

    private void openPage(Player player, List<SpawnerData> spawners, int page) {
        int total = spawners.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / SPAWNERS_PER_PAGE));
        page = Math.max(1, Math.min(page, totalPages));

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("world", "Near (" + total + ")");
        titlePlaceholders.put("current", String.valueOf(page));
        titlePlaceholders.put("total", String.valueOf(totalPages));
        String title = languageManager.commandGui().title("gui_title_spawner_list", titlePlaceholders);

        Inventory inv = Bukkit.createInventory(
                new NearResultHolder(player.getUniqueId(), page, totalPages),
                54, title);

        int start = (page - 1) * SPAWNERS_PER_PAGE;
        int end = Math.min(start + SPAWNERS_PER_PAGE, total);
        for (int i = start; i < end; i++) {
            inv.addItem(createSpawnerInfoItem(spawners.get(i)));
        }

        if (page > 1) {
            inv.setItem(45, createNavigationButton(
                    Material.SPECTRAL_ARROW,
                    "general_navigation.previous_page",
                    Map.of("target_page", String.valueOf(page - 1))));
        }
        // Close button at center-bottom
        inv.setItem(49, createNavigationButton(Material.RED_STAINED_GLASS_PANE, "general_navigation.close"));
        if (page < totalPages) {
            inv.setItem(53, createNavigationButton(
                    Material.SPECTRAL_ARROW,
                    "general_navigation.next_page",
                    Map.of("target_page", String.valueOf(page + 1))));
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        player.openInventory(inv);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Event handler
    // ──────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onNearResultClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof NearResultHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        int currentPage = holder.getCurrentPage();
        int totalPages = holder.getTotalPages();

        // Close button
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Previous page
        if (slot == 45 && currentPage > 1) {
            List<SpawnerData> spawners = highlightManager.getSessionSpawners(holder.getPlayerUUID());
            openPage(player, spawners, currentPage - 1);
            return;
        }

        // Next page
        if (slot == 53 && currentPage < totalPages) {
            List<SpawnerData> spawners = highlightManager.getSessionSpawners(holder.getPlayerUUID());
            openPage(player, spawners, currentPage + 1);
            return;
        }

        // Spawner item click (slots 0-44)
        if (slot < 45 && isSpawnerItem(event.getCurrentItem())) {
            handleSpawnerClick(player, event.getCurrentItem());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Click helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void handleSpawnerClick(Player player, ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        String displayName = item.getItemMeta().getDisplayName();
        Matcher matcher = ID_PATTERN.matcher(displayName);
        if (!matcher.find()) return;

        String spawnerId = matcher.group(1);
        SpawnerData spawner = plugin.getSpawnerManager().getSpawnerById(spawnerId);
        if (spawner == null) {
            plugin.getMessageService().sendMessage(player, "list.teleport_failed");
            return;
        }

        String worldName = spawner.getSpawnerLocation().getWorld().getName();
        spawnerManagementGUI.openManagementMenu(player, spawnerId, worldName, 1);
    }

    private boolean isSpawnerItem(ItemStack item) {
        return item != null
                && SPAWNER_MATERIALS.contains(item.getType())
                && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Item creation
    // ──────────────────────────────────────────────────────────────────────────

    private ItemStack createSpawnerInfoItem(SpawnerData spawner) {
        EntityType entityType = spawner.getEntityType();
        Location loc = spawner.getSpawnerLocation();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        placeholders.put("entity", languageManager.getFormattedMobName(entityType));
        placeholders.put("size", String.valueOf(spawner.getStackSize()));
        if (spawner.getSpawnerStop().get()) {
            placeholders.put("status", languageManager.commandGui().name("spawner_item_list.status.inactive"));
        } else {
            placeholders.put("status", languageManager.commandGui().name("spawner_item_list.status.active"));
        }
        placeholders.put("x", String.valueOf(loc.getBlockX()));
        placeholders.put("y", String.valueOf(loc.getBlockY()));
        placeholders.put("z", String.valueOf(loc.getBlockZ()));
        String lastPlayer = spawner.getLastInteractedPlayer();
        placeholders.put("last_player", lastPlayer != null
                ? lastPlayer
                : languageManager.commandGui().name("spawner_item_list.last_player_none"));

        ItemStack spawnerItem;
        if (entityType == null) {
            spawnerItem = new ItemStack(Material.SPAWNER);
            spawnerItem.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.setDisplayName(languageManager.commandGui().name("spawner_item_list.name", placeholders));
                meta.setLore(Arrays.asList(languageManager.commandGui().lore("spawner_item_list.lore", placeholders)));
            });
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, meta -> {
                meta.setDisplayName(languageManager.commandGui().name("spawner_item_list.name", placeholders));
                meta.setLore(Arrays.asList(languageManager.commandGui().lore("spawner_item_list.lore", placeholders)));
            });
        }

        ItemTooltipUtil.hideTooltip(spawnerItem);
        return spawnerItem;
    }

    private ItemStack createNavigationButton(Material material, String namePath) {
        return createNavigationButton(material, namePath, Collections.emptyMap());
    }

    private ItemStack createNavigationButton(Material material, String namePath, Map<String, String> placeholders) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(languageManager.commandGui().name(namePath + ".name", placeholders));
            List<String> lore = languageManager.commandGui().loreList(namePath + ".lore", placeholders);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            button.setItemMeta(meta);
        }
        return button;
    }
}
