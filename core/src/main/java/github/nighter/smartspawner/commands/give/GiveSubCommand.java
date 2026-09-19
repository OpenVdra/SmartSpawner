package github.nighter.smartspawner.commands.give;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.utils.DynamicEntityValidator;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.utils.SpawnerDisplayName;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Material;
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

@NullMarked
public class GiveSubCommand extends BaseSubCommand {
    private final SpawnerItemFactory spawnerItemFactory;
    private static final int MAX_AMOUNT = 6400;

    public GiveSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        // Generate supported mobs list from DynamicEntityValidator
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

        // Order: /ss give <player> <spawner type> ...
        // Only online player names are suggested (no @a, @e, @p selectors)
        builder.then(Commands.argument("player", ArgumentTypes.player())
                .suggests(createPlayerSuggestions())
                .then(buildSmartSpawnerGiveCommand())
                .then(buildVanillaGiveCommand())
                .then(buildItemSpawnerGiveCommand()));

        return builder;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSmartSpawnerGiveCommand() {
        return Commands.literal("smart_spawner")
                .then(Commands.argument("configName", StringArgumentType.word())
                        .suggests(createSmartSpawnerSuggestions())
                        .executes(context -> executeGive(context, false, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                .executes(context -> executeGive(context, false,
                                        IntegerArgumentType.getInteger(context, "amount")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildVanillaGiveCommand() {
        return Commands.literal("vanilla_spawner")
                .then(Commands.argument("mobType", StringArgumentType.word())
                        .suggests(createMobSuggestions())
                        .executes(context -> executeGive(context, true, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                .executes(context -> executeGive(context, true,
                                        IntegerArgumentType.getInteger(context, "amount")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildItemSpawnerGiveCommand() {
        return Commands.literal("item_spawner")
                .then(Commands.argument("configName", StringArgumentType.word())
                        .suggests(createItemSpawnerSuggestions())
                        .executes(context -> executeGiveItemSpawner(context, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                .executes(context -> executeGiveItemSpawner(context,
                                        IntegerArgumentType.getInteger(context, "amount")))));
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

    private SuggestionProvider<CommandSourceStack> createSmartSpawnerSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            plugin.getSpawnerSettingsConfig().getDefinitionNames().stream()
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

    private SuggestionProvider<CommandSourceStack> createItemSpawnerSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            plugin.getItemSpawnerSettingsConfig().getDefinitionNames().stream()
                    .filter(name -> name.startsWith(input)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return 0;
    }

    private int executeGive(CommandContext<CommandSourceStack> context, boolean isVanilla, int amount) {
        CommandSender sender = context.getSource().getSender();
        
        // Log command execution
        logCommandExecution(context);

        try {
            // Get the player selector and resolve it
            var playerSelector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
            List<Player> players = playerSelector.resolve(context.getSource());

            if (players.isEmpty()) {
                plugin.getMessageService().sendMessage(sender, "give.player_not_found");
                return 0;
            }

            Player target = players.get(0); // Get the first (and typically only) player from the selector
            String mobType = isVanilla ? StringArgumentType.getString(context, "mobType") : null;

            // Validate mob type (case insensitive check)
            EntityType entityType;
            ItemStack spawnerItem;

            if (isVanilla) {
                try { entityType = EntityType.valueOf(mobType.toUpperCase()); }
                catch (IllegalArgumentException e) {
                    plugin.getMessageService().sendMessage(sender, "give.invalid_mob_type"); return 0;
                }
                // Use the correct method for vanilla spawners
                spawnerItem = spawnerItemFactory.createVanillaSpawnerItem(entityType, amount);
            } else {
                String configName = StringArgumentType.getString(context, "configName");
                var definition = plugin.getSpawnerSettingsConfig().getDefinition(configName);
                if (definition == null) {
                    plugin.getMessageService().sendMessage(sender, "give.invalid_mob_type"); return 0;
                }
                entityType = definition.entityType();
                spawnerItem = spawnerItemFactory.createSmartSpawnerItem(definition.name(), amount);
            }

            giveOrDropOverflow(target, spawnerItem);

            // Play sound
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            // Get formatted entity names for placeholders
            String entityName = plugin.getLanguageManager().getFormattedMobName(entityType);
            String smallCapsEntityName = plugin.getLanguageManager().getSmallCaps(entityName);

            // Create placeholders for sender message
            HashMap<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("player", target.getName());
            senderPlaceholders.put("entity", entityName);
            senderPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);
            senderPlaceholders.put("amount", String.valueOf(amount));

            // Create placeholders for target message
            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", entityName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);

            // Send messages with placeholders
            plugin.getMessageService().sendMessage(sender, "give.spawner_given", senderPlaceholders);
            plugin.getMessageService().sendMessage(target, "give.spawner_received", targetPlaceholders);

            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing give command: " + e.getMessage());
            return 0;
        }
    }

    private int executeGiveItemSpawner(CommandContext<CommandSourceStack> context, int amount) {
        CommandSender sender = context.getSource().getSender();
        
        // Log command execution
        logCommandExecution(context);

        try {
            // Get the player selector and resolve it
            var playerSelector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
            List<Player> players = playerSelector.resolve(context.getSource());

            if (players.isEmpty()) {
                plugin.getMessageService().sendMessage(sender, "give.player_not_found");
                return 0;
            }

            Player target = players.get(0);
            String configName = StringArgumentType.getString(context, "configName");
            var definition = plugin.getItemSpawnerSettingsConfig().getDefinition(configName);
            if (definition == null) {
                plugin.getMessageService().sendMessage(sender, "give.invalid_item_spawner");
                return 0;
            }
            Material itemMaterial = definition.material();

            ItemStack spawnerItem = spawnerItemFactory.createItemSpawnerItem(definition.name(), amount);

            giveOrDropOverflow(target, spawnerItem);

            // Play sound
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            // Get formatted item names for placeholders
            String itemName = SpawnerDisplayName.ofItem(plugin, definition.name(), itemMaterial);
            String smallCapsItemName = plugin.getLanguageManager().getSmallCaps(itemName);

            // Create placeholders for sender message (use "entity" key for compatibility with spawner messages)
            HashMap<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("player", target.getName());
            senderPlaceholders.put("entity", itemName);
            senderPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsItemName);
            senderPlaceholders.put("amount", String.valueOf(amount));

            // Create placeholders for target message
            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", itemName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsItemName);

            // Send messages with placeholders (use same keys as regular spawners)
            plugin.getMessageService().sendMessage(sender, "give.spawner_given", senderPlaceholders);
            plugin.getMessageService().sendMessage(target, "give.spawner_received", targetPlaceholders);

            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing give item spawner command: " + e.getMessage());
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
