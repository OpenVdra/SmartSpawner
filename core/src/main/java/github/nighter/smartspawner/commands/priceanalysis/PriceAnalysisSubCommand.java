package github.nighter.smartspawner.commands.priceanalysis;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager.PriceSourceMode;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.stream.Collectors;

@NullMarked
public class PriceAnalysisSubCommand extends BaseSubCommand {

    private static final String ARG_SPAWNER_TYPE = "spawnerType";

    public PriceAnalysisSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "priceanalysis"; }

    @Override
    public String getPermission() { return "smartspawner.command.priceanalysis"; }

    @Override
    public String getDescription() { return "Show price analysis for sellable items across spawners"; }

    // Override build() to wire optional spawner type argument with tab completion
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // /ss priceanalysis — all spawners
        builder.executes(context -> {
            logCommandExecution(context);
            return runAnalysis(context, null);
        });

        // /ss priceanalysis <spawnerType> — specific spawner type
        builder.then(Commands.argument(ARG_SPAWNER_TYPE, StringArgumentType.word())
                .suggests(createSpawnerTypeSuggestions())
                .executes(context -> {
                    logCommandExecution(context);
                    String arg = StringArgumentType.getString(context, ARG_SPAWNER_TYPE);
                    return runAnalysis(context, arg.toUpperCase());
                })
        );

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return runAnalysis(context, null);
    }

    /**
     * Suggests entity types that actually have spawners on this server.
     */
    private SuggestionProvider<CommandSourceStack> createSpawnerTypeSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            getActiveSpawnerTypes().stream()
                    .map(e -> e.name().toLowerCase())
                    .filter(name -> name.startsWith(input))
                    .sorted()
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /**
     * Returns the set of EntityTypes that have at least one spawner loaded.
     */
    private Set<EntityType> getActiveSpawnerTypes() {
        Set<EntityType> types = new LinkedHashSet<>();
        for (SpawnerData spawner : plugin.getSpawnerManager().getAllSpawners()) {
            if (!spawner.getValidLootItems().isEmpty()) {
                types.add(spawner.getEntityType());
            }
        }
        return types;
    }

    private int runAnalysis(CommandContext<CommandSourceStack> context, String filterType) {
        CommandSender sender = context.getSource().getSender();
        ItemPriceManager priceManager = plugin.getItemPriceManager();
        SpawnerManager spawnerManager = plugin.getSpawnerManager();

        // Build map: EntityType -> Set<Material>
        Map<EntityType, Set<Material>> materialsByType = new LinkedHashMap<>();
        for (SpawnerData spawner : spawnerManager.getAllSpawners()) {
            EntityType type = spawner.getEntityType();

            // Filter to specific type if argument provided
            if (filterType != null && !type.name().equalsIgnoreCase(filterType)) continue;

            List<LootItem> lootItems = spawner.getValidLootItems();
            if (lootItems.isEmpty()) continue;

            Set<Material> materials = materialsByType.computeIfAbsent(type, k -> new LinkedHashSet<>());
            for (LootItem loot : lootItems) {
                materials.add(loot.material());
            }
        }

        if (materialsByType.isEmpty()) {
            if (filterType != null) {
                sender.sendMessage(ChatColor.RED + "No spawners found for type: " + ChatColor.YELLOW + filterType
                        + ChatColor.RED + ". Use /ss priceanalysis for the full list.");
            } else {
                sender.sendMessage(ChatColor.RED + "No spawners found or no sellable items configured.");
            }
            return 0;
        }

        // Header
        String scope = filterType != null
                ? formatEntityName(filterType)
                : "All Spawners";
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "════ Price Analysis: " + scope + " ════");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.YELLOW + priceManager.getPriceSourceMode().name()
                + ChatColor.GRAY + "  Source: " + ChatColor.AQUA + getActiveSourceLabel(priceManager));
        sender.sendMessage("");

        int totalItems = 0, shopPriced = 0, customPriced = 0, notConfigured = 0;

        for (Map.Entry<EntityType, Set<Material>> entry : materialsByType.entrySet()) {
            Set<Material> materials = entry.getValue();

            // Show spawner type header only in "all" mode (single type is already in the title)
            if (filterType == null) {
                sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD
                        + formatEntityName(entry.getKey().name())
                        + ChatColor.GRAY + " (" + materials.size() + " items)");
            }

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

                String indent = filterType != null ? "" : "  ";
                sender.sendMessage(ChatColor.GRAY + indent + lineColor + formatMaterialName(material)
                        + ChatColor.DARK_GRAY + " → " + priceStr + "  " + sourceLabel);
            }
            if (filterType == null) sender.sendMessage("");
        }

        // Summary
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "════ Summary ════");
        sender.sendMessage(ChatColor.GRAY + "Total items:           " + ChatColor.WHITE + totalItems);
        sender.sendMessage(ChatColor.GREEN + "From shop:             " + ChatColor.WHITE + shopPriced);
        sender.sendMessage(ChatColor.YELLOW + "From custom/fallback:  " + ChatColor.WHITE + customPriced);
        sender.sendMessage(ChatColor.RED + "Not configured:        " + ChatColor.WHITE + notConfigured);

        if (notConfigured > 0) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC
                    + "⚠ " + notConfigured + " item(s) have no price. " + getMissingPriceHint(priceManager.getPriceSourceMode()));
        }

        return 1;
    }

    private String getMissingPriceHint(PriceSourceMode mode) {
        return switch (mode) {
            case SHOP_ONLY       -> "Add them to your shop plugin — custom prices are ignored in SHOP_ONLY mode.";
            case CUSTOM_ONLY     -> "Add them to item_prices.yml — shop prices are ignored in CUSTOM_ONLY mode.";
            case SHOP_PRIORITY   -> "Add them to your shop plugin (preferred) or item_prices.yml as fallback.";
            case CUSTOM_PRIORITY -> "Add them to item_prices.yml (preferred) or your shop plugin as fallback.";
        };
    }

    private PriceSource resolveSource(ItemPriceManager pm, Material material) {
        PriceSourceMode mode = pm.getPriceSourceMode();
        double shopPrice   = pm.getShopPriceFor(material);
        double customPrice = pm.getCustomPriceFor(material);

        return switch (mode) {
            case SHOP_ONLY    -> shopPrice   > 0 ? PriceSource.SHOP   : PriceSource.NONE;
            case CUSTOM_ONLY  -> customPrice > 0 ? PriceSource.CUSTOM : PriceSource.NONE;
            case SHOP_PRIORITY -> {
                if (shopPrice   > 0) yield PriceSource.SHOP;
                if (customPrice > 0) yield PriceSource.FALLBACK;
                yield PriceSource.NONE;
            }
            case CUSTOM_PRIORITY -> {
                if (customPrice > 0) yield PriceSource.CUSTOM;
                if (shopPrice   > 0) yield PriceSource.FALLBACK;
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

    private String formatEntityName(String name) {
        return Arrays.stream(name.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String formatEntityName(EntityType type) {
        return formatEntityName(type.name());
    }

    private String formatMaterialName(Material material) {
        return Arrays.stream(material.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private enum PriceSource { SHOP, CUSTOM, FALLBACK, NONE }
}
