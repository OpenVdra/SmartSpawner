package github.nighter.smartspawner.commands.editloot;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the two inventories of the loot editor: the loot list and the item capture screen.
 *
 * <p>Slot numbers are shared with {@link LootEditorHandler}, which is why they live here as constants
 * rather than as literals on both sides.</p>
 */
public class LootEditorUI {

    static final int LOOT_SIZE = 27;
    static final int LOOT_START = 0;
    static final int LOOT_END = 26;

    static final int CAPTURE_SIZE = 27;
    static final int CAPTURE_SLOT = 13;
    static final int CAPTURE_CANCEL = 18;
    static final int CAPTURE_CONFIRM = 26;

    private final SmartSpawner plugin;
    private final LootEditorService service;

    public LootEditorUI(SmartSpawner plugin, LootEditorService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    // ============== Loot list ==============

    /**
     * The loot rows of one entry, as the items they actually drop.
     *
     * <p>A single green pane sits directly after the last row: it is the "add another item" button,
     * and it moves to the next slot whenever a row is added, until all 27 slots are full.</p>
     */
    public void openLootList(Player player, LootEditorTarget target, String entryKey) {
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("entry", entryKey.substring(entryKey.lastIndexOf('.') + 1));

        Inventory inventory = Bukkit.createInventory(
                new LootListHolder(target, entryKey), LOOT_SIZE,
                lang().commandGui().title(target.getTitleKey(), titlePlaceholders));

        int slot = LOOT_START;
        for (LootEditorService.LootView loot : service.readLootList(target, entryKey)) {
            if (slot > LOOT_END) {
                break;
            }
            inventory.setItem(slot++, buildLootIcon(loot));
        }
        if (slot <= LOOT_END) {
            inventory.setItem(slot, simpleItem(Material.LIME_STAINED_GLASS_PANE,
                    "editloot.add_loot", Map.of()));
        }

        player.openInventory(inventory);
    }

    private ItemStack buildLootIcon(LootEditorService.LootView loot) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", loot.key());
        placeholders.put("amount", loot.amountLabel());
        placeholders.put("chance", trim(loot.chance()));
        String durability = loot.durabilityLabel();
        placeholders.put("durability", durability == null ? "-" : durability);

        if (loot.isBroken()) {
            // A broken row cannot be rendered as its item, so its raw value is shown for the admin to fix.
            placeholders.put("item", loot.rawItem());
            return simpleItem(Material.BARRIER, "editloot.loot_broken", placeholders);
        }

        // Show the real item so the admin sees exactly what drops, then overwrite its text. The item name
        // is inserted as a component so the lore reads a readable, translated name (a tipped arrow shows
        // "Arrow of Strength") instead of the raw config value, which can be a long nbt blob.
        ItemStack icon = loot.preview().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang().commandGui().name("editloot.loot_entry.name", placeholders));
            List<Component> lore = lang().commandGui().loreWithItemName(
                    "editloot.loot_entry.lore", placeholders, "{item}", lang().getItemDisplayName(loot.preview()));
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    // ============== Item capture ==============

    public void openItemCapture(Player player, LootCaptureHolder holder) {
        String titleKey = holder.getPurpose() == LootCaptureHolder.Purpose.ADD_LOOT
                ? "editloot.capture_add_title"
                : "editloot.capture_replace_title";

        Inventory inventory = Bukkit.createInventory(holder, CAPTURE_SIZE,
                lang().commandGui().title(titleKey, Map.of()));

        ItemStack filler = simpleItem(Material.GRAY_STAINED_GLASS_PANE, "editloot.capture_hint", Map.of());
        for (int i = 0; i < CAPTURE_SIZE; i++) {
            inventory.setItem(i, filler.clone());
        }

        inventory.setItem(CAPTURE_SLOT, null);
        inventory.setItem(CAPTURE_CANCEL, simpleItem(Material.RED_STAINED_GLASS_PANE,
                "editloot.cancel", Map.of()));
        inventory.setItem(CAPTURE_CONFIRM, simpleItem(Material.LIME_STAINED_GLASS_PANE,
                "editloot.confirm", Map.of()));

        player.openInventory(inventory);
    }

    // ============== Item helpers ==============

    private ItemStack simpleItem(Material material, String key, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        applyText(item, key, placeholders);
        return item;
    }

    /**
     * The language section takes the full path to the value, not to the block holding it, so the
     * {@code .name} and {@code .lore} leaves have to be named here.
     */
    private void applyText(ItemStack item, String key, Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(lang().commandGui().name(key + ".name", placeholders));
        List<String> lore = new ArrayList<>(lang().commandGui().loreList(key + ".lore", placeholders));
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    /** Drops a trailing {@code .0} so whole percentages read as whole numbers. */
    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
