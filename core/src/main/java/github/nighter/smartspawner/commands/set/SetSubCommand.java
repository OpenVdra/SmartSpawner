package github.nighter.smartspawner.commands.set;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import github.nighter.smartspawner.utils.TimeFormatter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@NullMarked
public class SetSubCommand extends BaseSubCommand {
    private static final int TARGET_DISTANCE = 8;
    private static final int[] STACK_SIZE_SUGGESTIONS = {1, 10, 100, 1000, 10000};
    private static final int[] RANGE_SUGGESTIONS = {8, 16, 24, 32, 64};
    private static final String[] DELAY_SUGGESTIONS = {"5s", "10s", "25s", "1m", "5m"};

    private final SpawnerManager spawnerManager;
    private final TimeFormatter timeFormatter;

    public SetSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.timeFormatter = new TimeFormatter(plugin);
    }

    @Override
    public String getName() {
        return "set";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.set";
    }

    @Override
    public String getDescription() {
        return "Set smart spawner properties";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        builder.executes(this::execute);

        builder.then(buildIntegerPropertyCommand(SetProperty.STACK_SIZE, STACK_SIZE_SUGGESTIONS));
        builder.then(buildIntegerPropertyCommand(SetProperty.RANGE, RANGE_SUGGESTIONS));
        builder.then(buildDelayCommand());

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        logCommandExecution(context);
        plugin.getMessageService().sendMessage(context.getSource().getSender(), "set.usage");
        return 0;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildIntegerPropertyCommand(SetProperty property, int[] suggestions) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> valueArgument =
                Commands.argument("value", IntegerArgumentType.integer(1))
                        .suggests(createIntegerSuggestions(suggestions, property.valueTooltip()))
                        .executes(context -> executeSet(
                                context,
                                property,
                                String.valueOf(IntegerArgumentType.getInteger(context, "value")),
                                false));

        addLocationArguments(valueArgument, property, context ->
                String.valueOf(IntegerArgumentType.getInteger(context, "value")));

        return Commands.literal(property.commandName()).then(valueArgument);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildDelayCommand() {
        RequiredArgumentBuilder<CommandSourceStack, String> valueArgument =
                Commands.argument("value", StringArgumentType.word())
                        .suggests(createDelaySuggestions())
                        .executes(context -> executeSet(
                                context,
                                SetProperty.DELAY,
                                StringArgumentType.getString(context, "value"),
                                false));

        addLocationArguments(valueArgument, SetProperty.DELAY,
                context -> StringArgumentType.getString(context, "value"));

        return Commands.literal(SetProperty.DELAY.commandName()).then(valueArgument);
    }

    private <T> void addLocationArguments(
            RequiredArgumentBuilder<CommandSourceStack, T> valueArgument,
            SetProperty property,
            ValueReader valueReader
    ) {
        valueArgument.then(
                Commands.argument("world", StringArgumentType.word())
                        .suggests(createWorldSuggestions())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .suggests(createCoordinateSuggestions(Coordinate.X))
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .suggests(createCoordinateSuggestions(Coordinate.Y))
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .suggests(createCoordinateSuggestions(Coordinate.Z))
                                                .executes(context -> executeSet(
                                                        context,
                                                        property,
                                                        valueReader.read(context),
                                                        true))))));
    }

    private int executeSet(
            CommandContext<CommandSourceStack> context,
            SetProperty property,
            String rawValue,
            boolean explicitLocation
    ) {
        logCommandExecution(context);

        CommandSender sender = context.getSource().getSender();
        Long parsedValue = parseValue(sender, property, rawValue);
        if (parsedValue == null) {
            return 0;
        }

        SpawnerData spawner = explicitLocation
                ? getSpawnerFromArguments(context, sender)
                : getSpawnerPlayerIsLookingAt(sender);
        if (spawner == null) {
            return 0;
        }

        String oldValue = getDisplayValue(property, spawner);
        if (!applyValue(sender, property, spawner, parsedValue)) {
            return 0;
        }

        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());

        Location location = spawner.getSpawnerLocation();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("property", property.commandName());
        placeholders.put("old_value", oldValue);
        placeholders.put("new_value", getDisplayValue(property, spawner));
        placeholders.put("world", location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));

        plugin.getMessageService().sendMessage(sender, "set.success", placeholders);
        return 1;
    }

    private Long parseValue(CommandSender sender, SetProperty property, String rawValue) {
        if (property == SetProperty.DELAY) {
            long ticks = timeFormatter.parseTimeToTicks(rawValue, -1L);
            if (ticks <= 0L) {
                sendInvalidValue(sender, property);
                return null;
            }
            return ticks;
        }

        try {
            int value = Integer.parseInt(rawValue);
            if (value < 1) {
                sendInvalidValue(sender, property);
                return null;
            }
            return (long) value;
        } catch (NumberFormatException ignored) {
            sendInvalidValue(sender, property);
            return null;
        }
    }

    private void sendInvalidValue(CommandSender sender, SetProperty property) {
        sendInvalid(sender);
    }

    private void sendInvalid(CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "set.invalid");
    }

    private SpawnerData getSpawnerFromArguments(CommandContext<CommandSourceStack> context, CommandSender sender) {
        String worldName = StringArgumentType.getString(context, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sendInvalid(sender);
            return null;
        }

        Location location = new Location(
                world,
                IntegerArgumentType.getInteger(context, "x"),
                IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z")
        );

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(location);
        if (spawner == null) {
            sendInvalid(sender);
        }
        return spawner;
    }

    private SpawnerData getSpawnerPlayerIsLookingAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "set.location_required");
            return null;
        }

        Block targetBlock = getTargetSpawnerBlock(player);
        if (targetBlock == null) {
            sendInvalid(sender);
            return null;
        }

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(targetBlock.getLocation());
        if (spawner == null) {
            sendInvalid(sender);
        }
        return spawner;
    }

    private Block getTargetSpawnerBlock(Player player) {
        Block targetBlock = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (targetBlock == null || targetBlock.getType() != Material.SPAWNER) {
            return null;
        }
        return targetBlock;
    }

    private boolean applyValue(CommandSender sender, SetProperty property, SpawnerData spawner, long value) {
        switch (property) {
            case STACK_SIZE -> {
                int stackSize = toInt(value);
                if (stackSize > spawner.getMaxStackSize() && stackSize > spawner.getStackSize()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("value", String.valueOf(stackSize));
                    placeholders.put("max", String.valueOf(spawner.getMaxStackSize()));
                    plugin.getMessageService().sendMessage(sender, "set.stack_size_exceeds_max", placeholders);
                    return false;
                }
                spawner.setStackSize(stackSize);
            }
            case RANGE -> {
                spawner.getDataLock().lock();
                try {
                    spawner.setSpawnerRange(toInt(value));
                } finally {
                    spawner.getDataLock().unlock();
                }
            }
            case DELAY -> {
                spawner.getDataLock().lock();
                try {
                    spawner.setSpawnDelay(value);
                    spawner.setLastSpawnTime(System.currentTimeMillis());
                } finally {
                    spawner.getDataLock().unlock();
                }
            }
        }
        return true;
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private String getDisplayValue(SetProperty property, SpawnerData spawner) {
        return switch (property) {
            case STACK_SIZE -> String.valueOf(spawner.getStackSize());
            case RANGE -> String.valueOf(spawner.getSpawnerRange());
            case DELAY -> timeFormatter.formatTicks(spawner.getSpawnDelay());
        };
    }

    private SuggestionProvider<CommandSourceStack> createIntegerSuggestions(int[] values, String tooltip) {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (int value : values) {
                String suggestion = String.valueOf(value);
                if (suggestion.startsWith(input)) {
                    builder.suggest(value, new LiteralMessage(tooltip));
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> createDelaySuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String suggestion : DELAY_SUGGESTIONS) {
                if (suggestion.startsWith(input)) {
                    builder.suggest(suggestion, new LiteralMessage("Spawner delay, for example 25s or 1m"));
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> createWorldSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (worldName.toLowerCase(Locale.ROOT).startsWith(input)) {
                    builder.suggest(worldName, new LiteralMessage("World name"));
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> createCoordinateSuggestions(Coordinate coordinate) {
        return (context, builder) -> {
            if (context.getSource().getSender() instanceof Player player) {
                Location location = player.getLocation();
                Block targetBlock = getTargetSpawnerBlock(player);
                if (targetBlock != null) {
                    location = targetBlock.getLocation();
                }

                builder.suggest(coordinate.value(location), new LiteralMessage(coordinate.tooltip()));
            }
            return builder.buildFuture();
        };
    }

    @FunctionalInterface
    private interface ValueReader {
        String read(CommandContext<CommandSourceStack> context);
    }

    private enum Coordinate {
        X("X coordinate") {
            @Override
            int value(Location location) {
                return location.getBlockX();
            }
        },
        Y("Y coordinate") {
            @Override
            int value(Location location) {
                return location.getBlockY();
            }
        },
        Z("Z coordinate") {
            @Override
            int value(Location location) {
                return location.getBlockZ();
            }
        };

        private final String tooltip;

        Coordinate(String tooltip) {
            this.tooltip = tooltip;
        }

        abstract int value(Location location);

        String tooltip() {
            return tooltip;
        }
    }

    private enum SetProperty {
        STACK_SIZE("stack_size", "Stack size"),
        RANGE("range", "Spawner range"),
        DELAY("delay", "Spawner delay");

        private final String commandName;
        private final String valueTooltip;

        SetProperty(String commandName, String valueTooltip) {
            this.commandName = commandName;
            this.valueTooltip = valueTooltip;
        }

        String commandName() {
            return commandName;
        }

        String valueTooltip() {
            return valueTooltip;
        }
    }
}
