package github.nighter.smartspawner.commands.hologram;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class HologramSubCommand extends BaseSubCommand {
    private final SpawnerManager spawnerManager;
    private final HologramClearSubCommand clearSubCommand;

    public HologramSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.clearSubCommand = new HologramClearSubCommand(plugin);
    }

    @Override
    public String getName() {
        return "hologram";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.hologram";
    }

    @Override
    public String getDescription() {
        return "Toggle hologram display for spawners";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        
        // Add the toggle functionality as the default execution
        builder.executes(this::execute);
        
        // Add the clear subcommand
        builder.then(clearSubCommand.build());
        
        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        
        // Log command execution
        logCommandExecution(context);

        try {
            // Toggle hologram state
            boolean newValue = !plugin.getConfig().getBoolean("hologram.enabled");

            // Set new value in config
            plugin.getConfig().set("hologram.enabled", newValue);

            // Save the config (this only saves the current config, it doesn't create a new one)
            plugin.saveConfig();

            // Update all holograms
            spawnerManager.refreshAllHolograms();

            // Send message to player using MessageService
            String messageKey = newValue ? "command.hologram.enabled" : "command.hologram.disabled";
            plugin.getMessageService().sendMessage(sender, messageKey);

            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling holograms: " + e.getMessage());
            return 0;
        }
    }
}
