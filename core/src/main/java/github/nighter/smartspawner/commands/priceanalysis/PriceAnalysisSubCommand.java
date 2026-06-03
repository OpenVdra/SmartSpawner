package github.nighter.smartspawner.commands.priceanalysis;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager.PriceSourceMode;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public class PriceAnalysisSubCommand extends BaseSubCommand {

    public PriceAnalysisSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "priceanalysis";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.priceanalysis";
    }

    @Override
    public String getDescription() {
        return "Show price analysis for all sellable items across all spawners";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        ItemPriceManager priceManager = plugin.getItemPriceManager();
        SpawnerManager spawnerManager = plugin.getSpawnerManager();

        // Collect unique materials across all spawners, grouped by spawner type
        // Map: EntityType -> Set<Material>
        Map<EntityType, Set<Material>> materialsByType = new LinkedHashMap<>();

        for (SpawnerData spawner : spawnerManager.getAllSpawners()) {
            EntityType type = spawner.getEntityType();
            List<LootItem> lootItems = spawner.getValidLootItems();

            if (lootItems.isEmpty()) continue;

            Set<Material> materials = materialsByType.computeIfAbsent(type, k -> new LinkedHashSet<>());
            for (LootItem loot : lootItems) {
                materials.add(loot.material());
            }
        }

        if (materialsByType.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No spawners found or no sellable items configured.");
            return 0;
        }

        // --- Header ---
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "════ SmartSpawner Price Analysis ════");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.YELLOW + priceManager.getPriceSourceMode().name()
                + ChatColor.GRAY + "  Source: " + ChatColor.AQUA + getActiveSourceLabel(priceManager));
        sender.sendMessage("");

        int totalItems = 0;
        int shopPriced = 0;
        int customPriced = 0;
        int notConfigured = 0;

        // --- Per spawner type breakdown ---
        for (Map.Entry<EntityType, Set<Material>> entry : materialsByType.entrySet()) {
            EntityType entityType = entry.getKey();
            Set<Material> materials = entry.getValue();

            sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD
                    + formatEntityName(entityType) + ChatColor.GRAY + " (" + materials.size() + " items)");

            for (Material material : materials) {
                totalItems++;

                double finalPrice = priceManager.getPrice(material);
                PriceSource source = resolveSource(priceManager, material);

                String priceStr;
                String sourceLabel;
                ChatColor lineColor;

                switch (source) {
                    case SHOP -> {
                        shopPriced++;
                        lineColor = ChatColor.GREEN;
                        sourceLabel = ChatColor.GREEN + "[Shop]";
                        priceStr = ChatColor.WHITE + String.format("$%.2f", finalPrice);
                    }
                    case CUSTOM -> {
                        customPriced++;
                        lineColor = ChatColor.YELLOW;
                        sourceLabel = ChatColor.YELLOW + "[Custom]";
                        priceStr = ChatColor.WHITE + String.format("$%.2f", finalPrice);
                    }
                    case FALLBACK -> {
                        customPriced++;
                        lineColor = ChatColor.YELLOW;
                        sourceLabel = ChatColor.YELLOW + "[Fallback]";
                        priceStr = ChatColor.WHITE + String.format("$%.2f", finalPrice);
                    }
                    default -> {
                        notConfigured++;
                        lineColor = ChatColor.RED;
                        sourceLabel = ChatColor.RED + "[Not Configured]";
                        priceStr = ChatColor.RED + "N/A";
                    }
                }

                sender.sendMessage(ChatColor.GRAY + "  " + lineColor + formatMaterialName(material)
                        + ChatColor.DARK_GRAY + " → " + priceStr + "  " + sourceLabel);
            }
            sender.sendMessage("");
        }

        // --- Summary ---
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "════ Summary ════");
        sender.sendMessage(ChatColor.GRAY + "Total items:      " + ChatColor.WHITE + totalItems);
        sender.sendMessage(ChatColor.GREEN + "From shop:        " + ChatColor.WHITE + shopPriced);
        sender.sendMessage(ChatColor.YELLOW + "From custom/fallback: " + ChatColor.WHITE + customPriced);
        sender.sendMessage(ChatColor.RED + "Not configured:   " + ChatColor.WHITE + notConfigured);

        if (notConfigured > 0) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC
                    + "⚠ " + notConfigured + " item(s) have no price. Add them to item_prices.yml or your shop.");
        }

        return 1;
    }

    /**
     * Determines exactly which source provided the final price for a material,
     * respecting the configured price_source_mode.
     */
    private PriceSource resolveSource(ItemPriceManager pm, Material material) {
        PriceSourceMode mode = pm.getPriceSourceMode();
        double shopPrice  = pm.getShopPriceFor(material);
        double customPrice = pm.getCustomPriceFor(material);

        return switch (mode) {
            case SHOP_ONLY -> shopPrice > 0 ? PriceSource.SHOP : PriceSource.NONE;
            case CUSTOM_ONLY -> customPrice > 0 ? PriceSource.CUSTOM : PriceSource.NONE;
            case SHOP_PRIORITY -> {
                if (shopPrice > 0)   yield PriceSource.SHOP;
                if (customPrice > 0) yield PriceSource.FALLBACK;
                yield PriceSource.NONE;
            }
            case CUSTOM_PRIORITY -> {
                if (customPrice > 0) yield PriceSource.CUSTOM;
                if (shopPrice > 0)   yield PriceSource.FALLBACK;
                yield PriceSource.NONE;
            }
        };
    }

    private String getActiveSourceLabel(ItemPriceManager pm) {
        boolean hasShop   = pm.getShopIntegrationManager() != null && pm.getShopIntegrationManager().hasActiveProvider();
        boolean hasCustom = pm.customPricesEnabled;

        if (hasShop && hasCustom) return "Shop + Custom";
        if (hasShop)   return pm.getShopIntegrationManager().getActiveShopPlugin();
        if (hasCustom) return "Custom prices";
        return "None";
    }

    private String formatEntityName(EntityType type) {
        return Arrays.stream(type.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    private String formatMaterialName(Material material) {
        return Arrays.stream(material.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    private enum PriceSource { SHOP, CUSTOM, FALLBACK, NONE }
}
