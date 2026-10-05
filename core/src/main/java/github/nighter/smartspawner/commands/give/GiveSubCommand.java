package github.nighter.smartspawner.commands.give;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.config.ItemSpawnerSettingsConfig.ItemDefinition;
import github.nighter.smartspawner.spawner.config.SpawnerNames;
import github.nighter.smartspawner.spawner.config.SpawnerSettingsConfig.MobDefinition;
import github.nighter.smartspawner.utils.DynamicEntityValidator;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@NullMarked
public class GiveSubCommand extends BaseSubCommand {
    private final SpawnerItemFactory spawnerItemFactory;
    private static final int MAX_AMOUNT = 6400;

    public GiveSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.give";
    }

    @Override
    public String getDescription() {
        return "Give spawners to players";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // /ss give <player> <spawner> [amount]     spawner from spawner_mobs.yml or spawner_items.yml
        // /ss give <player> vanilla <mob> [amount]
        // Only online player names are suggested (no @a, @e, @p selectors)
        builder.then(Commands.argument("player", ArgumentTypes.player())
                .suggests(createPlayerSuggestions())
                .then(buildVanillaGiveCommand())
                .then(buildSpawnerGiveCommand()));

        return builder;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildVanillaGiveCommand() {
        return Commands.literal("vanilla")
                .then(Commands.argument("mobType", StringArgumentType.word())
                        .suggests(createMobSuggestions())
                        .executes(context -> giveVanilla(context, StringArgumentType.getString(context, "mobType"), 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                .executes(context -> giveVanilla(context, StringArgumentType.getString(context, "mobType"),
                                        IntegerArgumentType.getInteger(context, "amount")))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> buildSpawnerGiveCommand() {
        return Commands.argument("spawner", StringArgumentType.word())
                .suggests(createSpawnerSuggestions())
                .executes(context -> giveByName(context, StringArgumentType.getString(context, "spawner"), 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                        .executes(context -> giveByName(context, StringArgumentType.getString(context, "spawner"),
                                IntegerArgumentType.getInteger(context, "amount"))))
                // The old syntax, kept for console commands in shop, crate and vote plugins:
                // smart_spawner <name> [amount], item_spawner <name> [amount], vanilla_spawner <mob> [amount].
                // The keyword is parsed as <spawner> and the rest here. Not sent to players, so their
                // suggestions and argument hints only show the new syntax.
                .then(Commands.argument("legacy", StringArgumentType.greedyString())
                        .requires(source -> !(source.getSender() instanceof Player))
                        .suggests(createLegacySuggestions())
                        .executes(this::giveLegacy));
    }

    private SuggestionProvider<CommandSourceStack> createPlayerSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> createSpawnerSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            SpawnerNames.suggestionNames(plugin).stream()
                    .filter(name -> name.startsWith(input)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> createMobSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            DynamicEntityValidator.getValidEntities().stream()
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(input))
                    .sorted()
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** Completes names after an old-syntax keyword only; after a real spawner name it suggests nothing. */
    private SuggestionProvider<CommandSourceStack> createLegacySuggestions() {
        return (context, builder) -> {
            String keyword = StringArgumentType.getString(context, "spawner").toLowerCase(Locale.ROOT);
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            if (input.contains(" ")) return builder.buildFuture();
            Set<String> names = switch (keyword) {
                case "smart_spawner" -> plugin.getSpawnerSettingsConfig().getDefinitionNames();
                case "item_spawner" -> plugin.getItemSpawnerSettingsConfig().getDefinitionNames();
                default -> Set.of();
            };
            names.stream().filter(name -> name.startsWith(input)).sorted().forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return 0;
    }

    private int giveByName(CommandContext<CommandSourceStack> context, String name, int amount) {
        SpawnerNames.Resolved resolved = SpawnerNames.resolve(plugin, name);
        if (resolved == null) {
            return fail(context, "give.invalid_mob_type");
        }
        if (resolved.mob() != null) {
            return giveMob(context, resolved.mob(), amount);
        }
        return giveItem(context, resolved.item(), amount);
    }

    private int giveVanilla(CommandContext<CommandSourceStack> context, String mobType, int amount) {
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(mobType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(context, "give.invalid_mob_type");
        }
        return give(context, spawnerItemFactory.createVanillaSpawnerItem(entityType, amount),
                plugin.getLanguageManager().getFormattedMobName(entityType), amount);
    }

    private int giveLegacy(CommandContext<CommandSourceStack> context) {
        String keyword = StringArgumentType.getString(context, "spawner").toLowerCase(Locale.ROOT);
        String[] parts = StringArgumentType.getString(context, "legacy").trim().split(" +");
        if (parts.length > 2 || !keyword.endsWith("_spawner") || !SpawnerNames.RESERVED.contains(keyword)) {
            return fail(context, "give.invalid_mob_type");
        }

        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                return fail(context, "give.invalid_mob_type");
            }
        }

        String name = parts[0];
        switch (keyword) {
            case "vanilla_spawner":
                return giveVanilla(context, name, amount);
            case "item_spawner": {
                var item = plugin.getItemSpawnerSettingsConfig().resolve(name);
                return item == null ? fail(context, "give.invalid_item_spawner") : giveItem(context, item, amount);
            }
            default: {
                var mob = plugin.getSpawnerSettingsConfig().resolve(name);
                return mob == null ? fail(context, "give.invalid_mob_type") : giveMob(context, mob, amount);
            }
        }
    }

    private int giveMob(CommandContext<CommandSourceStack> context, MobDefinition mob, int amount) {
        String displayName = mob.displayName() != null ? mob.displayName()
                : plugin.getLanguageManager().getFormattedMobName(mob.entityType());
        return give(context, spawnerItemFactory.createSmartSpawnerItem(mob.name(), amount), displayName, amount);
    }

    private int giveItem(CommandContext<CommandSourceStack> context, ItemDefinition item, int amount) {
        String displayName = item.displayName() != null ? item.displayName()
                : plugin.getLanguageManager().getVanillaItemName(item.material());
        return give(context, spawnerItemFactory.createItemSpawnerItem(item.name(), amount), displayName, amount);
    }

    private int fail(CommandContext<CommandSourceStack> context, String messageKey) {
        logCommandExecution(context);
        plugin.getMessageService().sendMessage(context.getSource().getSender(), messageKey);
        return 0;
    }

    private int give(CommandContext<CommandSourceStack> context, ItemStack spawnerItem, String displayName, int amount) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        try {
            var playerSelector = context.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = playerSelector.resolve(context.getSource());
            if (players.isEmpty()) {
                plugin.getMessageService().sendMessage(sender, "give.player_not_found");
                return 0;
            }
            Player target = players.get(0);

            giveOrDropOverflow(target, spawnerItem);
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            String smallCapsName = plugin.getLanguageManager().getSmallCaps(displayName);

            HashMap<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("player", target.getName());
            senderPlaceholders.put("entity", displayName);
            senderPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsName);
            senderPlaceholders.put("amount", String.valueOf(amount));

            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", displayName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsName);

            plugin.getMessageService().sendMessage(sender, "give.spawner_given", senderPlaceholders);
            plugin.getMessageService().sendMessage(target, "give.spawner_received", targetPlaceholders);
            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing give command: " + e.getMessage());
            return 0;
        }
    }

    private void giveOrDropOverflow(Player target, ItemStack itemStack) {
        boolean droppedItems = false;

        for (ItemStack stack : splitIntoValidStacks(itemStack)) {
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack.clone());
            if (leftovers.isEmpty()) {
                continue;
            }

            droppedItems = true;
            for (ItemStack leftover : leftovers.values()) {
                for (ItemStack dropStack : splitIntoValidStacks(leftover)) {
                    target.getWorld().dropItem(target.getLocation(), dropStack);
                }
            }
        }

        if (droppedItems) {
            plugin.getMessageService().sendMessage(target, "give.inventory_full");
        }

        target.updateInventory();
    }

    private List<ItemStack> splitIntoValidStacks(ItemStack itemStack) {
        List<ItemStack> stacks = new ArrayList<>();
        int maxStackSize = Math.max(1, itemStack.getMaxStackSize());
        int remainingAmount = itemStack.getAmount();

        while (remainingAmount > 0) {
            int stackAmount = Math.min(maxStackSize, remainingAmount);
            ItemStack stack = itemStack.clone();
            stack.setAmount(stackAmount);
            stacks.add(stack);
            remainingAmount -= stackAmount;
        }

        return stacks;
    }
}
