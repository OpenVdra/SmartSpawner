package github.nighter.smartspawner.commands.clear;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ClearSubCommand extends BaseSubCommand {
    private final ClearHologramsSubCommand clearHologramsSubCommand;
    private final ClearGhostSpawnersSubCommand clearGhostSpawnersSubCommand;

    public ClearSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.clearHologramsSubCommand = new ClearHologramsSubCommand(plugin);
        this.clearGhostSpawnersSubCommand = new ClearGhostSpawnersSubCommand(plugin);
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.clear";
    }

    @Override
    public String getDescription() {
        return "Clear holograms or ghost spawners";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // Add subcommands
        builder.then(clearHologramsSubCommand.build());
        builder.then(clearGhostSpawnersSubCommand.build());

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        
        // When no subcommand is provided, show usage
        plugin.getMessageService().sendMessage(sender, "command.clear.usage");
        return 0;
    }
}
