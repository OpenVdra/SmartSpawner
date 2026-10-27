package github.nighter.smartspawner.commands.prices;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.language.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

public class PricesSubCommand extends BaseSubCommand {
    private final MessageService messageService;
    private final PricesGUI pricesGUI;

    public PricesSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.messageService = plugin.getMessageService();
        this.pricesGUI = new PricesGUI(plugin);
    }

    @Override
    public String getName() {
        return "prices";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.prices";
    }

    @Override
    public String getDescription() {
        return "View sell prices of spawner items";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        if (!isPlayer(context.getSource().getSender())) {
            return 0;
        }

        Player player = getPlayer(context.getSource().getSender());

        // Check if sell integration is available
        if (!plugin.hasSellIntegration()) {
            messageService.sendMessage(player, "command.prices.not_available");
            return 0;
        }

        pricesGUI.openPricesGUI(player, 1);
        return 1;
    }
}