package github.nighter.smartspawner.commands.reload;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.config.Config;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;


@NullMarked
public class ReloadSubCommand extends BaseSubCommand {

    public ReloadSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.reload";
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration and data";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        reloadAll(sender);
        return 1; // Success
    }

    private void reloadAll(CommandSender sender) {
        try {
            plugin.getMessageService().sendMessage(sender, "reload.start");

            // Clear all caches first to avoid using stale data during reload
            plugin.getSpawnerItemFactory().clearAllCaches();
            plugin.getMessageService().clearKeyExistsCache();

            // Reload all configurations
            plugin.reloadConfig();
            Config.reload(plugin);
            // Parsed times are cached; clear before spawners re-read their delay below.
            plugin.getTimeFormatter().clearCache();

            // Reload components in dependency order
            plugin.setUpHopperHandler();
            plugin.getItemPriceManager().reload();
            plugin.getSpawnerSettingsConfig().reload();
            plugin.getItemSpawnerSettingsConfig().reload();
            plugin.getSpawnerManager().reloadSpawnerDropsAndConfigs();
            plugin.getLanguageManager().reloadLanguages();

            // Reload GUI layout config FIRST (before MenuUI and ClickManager)
            plugin.getGuiLayoutConfig().loadLayout();
            plugin.getGuiButtonInteractionService().clear();

            // Then reload MenuUI and ClickManager (which depend on GUI layout)
            plugin.getSpawnerMenuUI().loadConfig();
            
            // Reload cached config values in click manager
            if (plugin.getSpawnerClickManager() != null) {
                plugin.getSpawnerClickManager().loadConfig();
            }
            plugin.getSpawnerExplosionListener().loadConfig();

            // Recheck timer placeholders after language reload to detect GUI configuration changes
            plugin.getSpawnerGuiViewManager().recheckTimerPlaceholders();

            // Reload factory AFTER its dependencies (loot registry, language manager)
            plugin.getSpawnerItemFactory().reload();
            plugin.getSpawnerManager().reloadAllHolograms();
            plugin.reload();

            // After plugin.reload(), which clears the parsed-time cache the autosave interval uses.
            if (plugin.getSpawnerStorage() != null) {
                plugin.getSpawnerStorage().reloadSettings();
            }

            plugin.getMessageService().sendMessage(sender, "reload.success");
        } catch (Exception e) {
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
            e.printStackTrace();
            plugin.getMessageService().sendMessage(sender, "reload.error");
        }
    }
}
