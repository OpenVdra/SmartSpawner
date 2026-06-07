package github.nighter.smartspawner.commands.config;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.config.Config;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@NullMarked
public class FolderConfigSubCommand extends BaseSubCommand {
    private static final String VALUE_ARGUMENT = "value";

    private final ConfigOption option;

    public FolderConfigSubCommand(SmartSpawner plugin, ConfigOption option) {
        super(plugin);
        this.option = option;
    }

    @Override
    public String getName() {
        return option.commandName();
    }

    @Override
    public String getPermission() {
        return option.permission();
    }

    @Override
    public String getDescription() {
        return option.description();
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        builder.executes(this::execute);
        builder.then(Commands.argument(VALUE_ARGUMENT, StringArgumentType.word())
                .suggests(createFolderSuggestions())
                .executes(this::executeSet));
        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        logCommandExecution(context);

        CommandSender sender = context.getSource().getSender();
        String currentValue = plugin.getConfig().getString(option.configKey(), option.defaultValue());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current", currentValue);
        placeholders.put("available", String.join(", ", getAvailableFolderNames()));
        plugin.getMessageService().sendMessage(sender, option.messagePrefix() + ".current", placeholders);
        return 1;
    }

    private int executeSet(CommandContext<CommandSourceStack> context) {
        logCommandExecution(context);

        CommandSender sender = context.getSource().getSender();
        String requestedValue = StringArgumentType.getString(context, VALUE_ARGUMENT);
        Optional<String> resolvedValue = resolveFolderName(requestedValue);
        if (resolvedValue.isEmpty()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("value", requestedValue);
            placeholders.put("available", String.join(", ", getAvailableFolderNames()));
            plugin.getMessageService().sendMessage(sender, option.messagePrefix() + ".invalid", placeholders);
            return 0;
        }

        String oldValue = plugin.getConfig().getString(option.configKey(), option.defaultValue());
        String newValue = resolvedValue.get();
        if (oldValue.equals(newValue)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("value", newValue);
            plugin.getMessageService().sendMessage(sender, option.messagePrefix() + ".already_set", placeholders);
            return 1;
        }

        try {
            plugin.getConfig().set(option.configKey(), newValue);
            plugin.saveConfig();
            reloadAfterChange();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("old_value", oldValue);
            placeholders.put("new_value", newValue);
            plugin.getMessageService().sendMessage(sender, option.messagePrefix() + ".success", placeholders);
            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to change " + option.displayName() + ": " + e.getMessage());
            plugin.getMessageService().sendMessage(sender, option.messagePrefix() + ".error");
            return 0;
        }
    }

    private void reloadAfterChange() {
        plugin.getSpawnerItemFactory().clearAllCaches();
        plugin.getMessageService().clearKeyExistsCache();
        Config.reload(plugin);

        if (option == ConfigOption.LANGUAGE) {
            plugin.getLanguageManager().reloadLanguages();
        }

        plugin.getGuiLayoutConfig().loadLayout();
        plugin.getSpawnerMenuUI().loadConfig();

        if (plugin.getSpawnerClickManager() != null) {
            plugin.getSpawnerClickManager().loadConfig();
        }
        if (plugin.getSpawnerExplosionListener() != null) {
            plugin.getSpawnerExplosionListener().loadConfig();
        }
        if (plugin.getSpawnerGuiViewManager() != null) {
            plugin.getSpawnerGuiViewManager().recheckTimerPlaceholders();
        }

        plugin.getSpawnerItemFactory().reload();
        plugin.getSpawnerManager().reloadAllHolograms();
        plugin.reload();
        plugin.getMessageService().clearKeyExistsCache();
    }

    private SuggestionProvider<CommandSourceStack> createFolderSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String folderName : getAvailableFolderNames()) {
                if (folderName.toLowerCase(Locale.ROOT).startsWith(input)) {
                    builder.suggest(folderName, new LiteralMessage(option.suggestionTooltip()));
                }
            }
            return builder.buildFuture();
        };
    }

    private Optional<String> resolveFolderName(String requestedValue) {
        for (String folderName : getAvailableFolderNames()) {
            if (folderName.equals(requestedValue)) {
                return Optional.of(folderName);
            }
        }

        String normalized = requestedValue.toLowerCase(Locale.ROOT);
        return getAvailableFolderNames().stream()
                .filter(folderName -> folderName.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    private java.util.List<String> getAvailableFolderNames() {
        File directory = new File(plugin.getDataFolder(), option.folderName());
        if (!directory.exists() && !directory.mkdirs()) {
            return java.util.List.of();
        }

        File[] folders = directory.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) {
            return java.util.List.of();
        }

        return Arrays.stream(folders)
                .map(File::getName)
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    public enum ConfigOption {
        LANGUAGE(
                "language",
                "language",
                "language",
                "en_US",
                "smartspawner.command.language",
                "Change the active SmartSpawner language",
                "language",
                "language",
                "Language folder"
        ),
        GUI_LAYOUT(
                "gui_layout",
                "gui_layout",
                "gui_layouts",
                "default",
                "smartspawner.command.gui_layout",
                "Change the active SmartSpawner GUI layout",
                "gui_layout",
                "GUI layout",
                "GUI layout folder"
        );

        private final String commandName;
        private final String configKey;
        private final String folderName;
        private final String defaultValue;
        private final String permission;
        private final String description;
        private final String messagePrefix;
        private final String displayName;
        private final String suggestionTooltip;

        ConfigOption(
                String commandName,
                String configKey,
                String folderName,
                String defaultValue,
                String permission,
                String description,
                String messagePrefix,
                String displayName,
                String suggestionTooltip
        ) {
            this.commandName = commandName;
            this.configKey = configKey;
            this.folderName = folderName;
            this.defaultValue = defaultValue;
            this.permission = permission;
            this.description = description;
            this.messagePrefix = messagePrefix;
            this.displayName = displayName;
            this.suggestionTooltip = suggestionTooltip;
        }

        String commandName() {
            return commandName;
        }

        String configKey() {
            return configKey;
        }

        String folderName() {
            return folderName;
        }

        String defaultValue() {
            return defaultValue;
        }

        String permission() {
            return permission;
        }

        String description() {
            return description;
        }

        String messagePrefix() {
            return messagePrefix;
        }

        String displayName() {
            return displayName;
        }

        String suggestionTooltip() {
            return suggestionTooltip;
        }
    }
}
