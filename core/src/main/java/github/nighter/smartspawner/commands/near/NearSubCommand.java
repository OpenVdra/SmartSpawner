package github.nighter.smartspawner.commands.near;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class NearSubCommand extends BaseSubCommand {

    private static final int DEFAULT_RADIUS = 50;
    /** Common radius suggestions shown in tab-completion. */
    private static final int[] SUGGESTED_RADII = {100, 1000, 10000};

    private final SpawnerHighlightManager highlightManager;
    private final NearResultGUI nearResultGUI;

    public NearSubCommand(SmartSpawner plugin, SpawnerHighlightManager highlightManager) {
        super(plugin);
        this.highlightManager = highlightManager;
        this.nearResultGUI = plugin.getNearResultGUI();
    }

    @Override
    public String getName() {
        return "near";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.near";
    }

    @Override
    public String getDescription() {
        return "Highlight nearby spawners through walls";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // /ss near           – scan with default radius
        builder.executes(this::execute);

        // /ss near <radius>  – scan with custom radius (1..MAX_RADIUS)
        // Tab-completion suggests common values; the argument still accepts any int in range.
        builder.then(
                Commands.argument("radius", IntegerArgumentType.integer(1, SpawnerHighlightManager.MAX_RADIUS))
                        .suggests((ctx, suggestions) -> {
                            for (int v : SUGGESTED_RADII) {
                                suggestions.suggest(v);
                            }
                            return suggestions.buildFuture();
                        })
                        .executes(context ->
                                executeScan(context, IntegerArgumentType.getInteger(context, "radius")))
        );

        // /ss near cancel / /ss near gui
        // Suggestions are computed on every tab-press so they appear only when the
        // player has an active scan session – no updateCommands() needed.
        builder.then(
                Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, suggestions) -> {
                            if (ctx.getSource().getSender() instanceof Player p
                                    && highlightManager.hasActiveSession(p.getUniqueId())) {
                                suggestions.suggest("cancel");
                                suggestions.suggest("gui");
                            }
                            return suggestions.buildFuture();
                        })
                        .executes(this::executeAction)
        );

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return executeScan(context, DEFAULT_RADIUS);
    }

    private int executeAction(CommandContext<CommandSourceStack> context) {
        String action = StringArgumentType.getString(context, "action");
        return switch (action.toLowerCase()) {
            case "cancel" -> executeCancel(context);
            case "gui"    -> executeGui(context);
            default       -> executeScan(context, DEFAULT_RADIUS);
        };
    }

    private int executeScan(CommandContext<CommandSourceStack> context, int radius) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command.player_only");
            return 0;
        }

        highlightManager.startScan(player, radius);
        return 1;
    }

    private int executeCancel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command.player_only");
            return 0;
        }

        highlightManager.cancelScan(player);
        return 1;
    }

    private int executeGui(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command.player_only");
            return 0;
        }

        nearResultGUI.openNearResultGUI(player, 1);
        return 1;
    }
}
