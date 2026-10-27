package github.nighter.smartspawner.language;

import github.nighter.smartspawner.language.section.MessageLanguageSection;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class MessageService {
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000));

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;

    // Static empty map to avoid creating new HashMap instances
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    // Cache for key existence checks to reduce repeated lookups
    private final Map<String, Boolean> keyExistsCache = new ConcurrentHashMap<>(128);

    /**
     * Sends a message to a CommandSender with no placeholders
     * @param sender The command sender to receive the message
     * @param key The message key
     */
    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Sends a message to a Player with no placeholders
     * @param player The player to receive the message
     * @param key The message key
     */
    public void sendMessage(Player player, String key) {
        sendMessage(player, key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Sends a message to a Player with placeholders
     * @param player The player to receive the message
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendMessage(Player player, String key, Map<String, String> placeholders) {
        sendMessage((CommandSender) player, key, placeholders);
    }

    /**
     * Sends every part a message key declares (chat line, title, subtitle, action bar, sound) in the
     * sender's language.
     * @param sender The command sender to receive the message
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        // Validate the message key exists (using cache to avoid lookups)
        if (!checkKeyExists(key)) {
            plugin.getLogger().warning("Message key not found: " + key);
            sendMissingMessageKeyNotice(sender, key);
            return;
        }

        MessageLanguageSection messages = languageManager.texts(sender).getMessages();
        Component message = messages.message(key, placeholders);
        if (message != null) {
            sender.sendMessage(message);
        }

        if (sender instanceof Player player) {
            sendPlayerSpecificContent(player, messages, key, placeholders);
        }
    }

    /**
     * Check if a key exists, using cache for efficiency
     * @param key The message key to check
     * @return true if the key exists, false otherwise
     */
    private boolean checkKeyExists(String key) {
        return keyExistsCache.computeIfAbsent(key, languageManager::keyExists);
    }

    /**
     * Clear the key existence cache (used during reloads)
     */
    public void clearKeyExistsCache() {
        keyExistsCache.clear();
    }

    /**
     * Sends a message to the console with no placeholders
     * @param key The message key
     */
    public void sendConsoleMessage(String key) {
        sendConsoleMessage(key, EMPTY_PLACEHOLDERS);
    }

    /**
     * Logs a message's chat line, without colours, in the server language.
     * @param key The message key
     * @param placeholders Map of placeholders to replace in the message
     */
    public void sendConsoleMessage(String key, Map<String, String> placeholders) {
        if (!languageManager.keyExists(key)) {
            plugin.getLogger().warning("Message key not found: " + key);
            return;
        }

        String message = languageManager.getMessageForConsole(key, placeholders);
        if (message != null) {
            plugin.getLogger().info(message);
        }
    }

    private void sendMissingMessageKeyNotice(CommandSender sender, String missingKey) {
        Component message = languageManager.texts(sender).getMessages().message("missing_key", Map.of("key", missingKey));
        if (message != null) {
            sender.sendMessage(message);
        }
    }

    /** Title and subtitle, action bar and sound, whichever the message has. */
    private void sendPlayerSpecificContent(Player player, MessageLanguageSection messages, String key,
                                           Map<String, String> placeholders) {
        Component title = messages.title(key, placeholders);
        Component subtitle = messages.subtitle(key, placeholders);
        if (title != null || subtitle != null) {
            player.showTitle(Title.title(
                    title != null ? title : Component.empty(),
                    subtitle != null ? subtitle : Component.empty(),
                    TITLE_TIMES));
        }

        Component actionBar = messages.actionBar(key, placeholders);
        if (actionBar != null) {
            player.sendActionBar(actionBar);
        }

        String soundName = messages.sound(key);
        if (soundName != null) {
            try {
                player.playSound(player.getLocation(), soundName, 1.0f, 1.0f);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound name for key " + key + ": " + soundName);
            }
        }
    }
}
