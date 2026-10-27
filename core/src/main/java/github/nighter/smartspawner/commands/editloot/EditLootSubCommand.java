package github.nighter.smartspawner.commands.editloot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;

/** Opens the loot editor for one spawner entry from {@code spawner_mobs.yml} or {@code spawner_items.yml}. */
@NullMarked
public class EditLootSubCommand extends BaseSubCommand {

    private final LootEditorService service;
    private final LootEditorUI ui;

    public EditLootSubCommand(SmartSpawner plugin, LootEditorService service, LootEditorUI ui) {
        super(plugin);
        this.service = service;
        this.ui = ui;
    }

    @Override
    public String getName() {
        return "editloot";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.editloot";
    }

    @Override
    public String getDescription() {
        return "Edit a spawner's loot table in game";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        builder.executes(context -> {
            logCommandExecution(context);
            return execute(context);
        });

        RequiredArgumentBuilder<CommandSourceStack, String> nameArgument =
                Commands.argument("name", StringArgumentType.word())
                        .suggests(nameSuggestions())
                        .executes(context -> {
                            logCommandExecution(context);
                            return open(context, StringArgumentType.getString(context, "name"));
                        });
        builder.then(nameArgument);
        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        plugin.getMessageService().sendMessage(context.getSource().getSender(), "command.editloot.usage");
        return 0;
    }

    private int open(CommandContext<CommandSourceStack> context, String name) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command.player_only");
            return 0;
        }

        LootEditorService.EntryRef ref = service.findEntry(name);
        if (ref == null) {
            plugin.getMessageService().sendMessage(player, "command.editloot.unknown_entry", Map.of("name", name));
            return 0;
        }

        ui.openLootList(player, ref.getTarget(), ref.getEntryKey());
        return 1;
    }

    private SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : service.listAllEntryNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }
}
