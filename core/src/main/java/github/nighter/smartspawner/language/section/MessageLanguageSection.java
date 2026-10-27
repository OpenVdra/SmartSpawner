package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.file.LegacyLanguageKeys;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.TextFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Messages from {@code messages.yml}. A message is one entry ({@code general.inventory_full}) with any
 * of {@code message}, {@code title}, {@code subtitle}, {@code action_bar}, {@code sound} and
 * {@code enabled}. The first locale in the chain that has the entry owns all of it, so a component the
 * owner deleted stays deleted instead of coming back from {@code en_US}.
 *
 * <p>Only the chat line may use {@code <click>} and {@code <hover>}; titles and action bars cannot.</p>
 */
public final class MessageLanguageSection {
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private final Supplier<LocaleData> localeSupplier;
    private final PlaceholderFormatter formatter;
    private final Consumer<String> legacyWarning;

    public MessageLanguageSection(Supplier<LocaleData> localeSupplier, PlaceholderFormatter formatter,
                                  Consumer<String> legacyWarning) {
        this.localeSupplier = localeSupplier;
        this.formatter = formatter;
        this.legacyWarning = legacyWarning;
    }

    /** The chat line with the prefix, or {@code null} when the message has none or is turned off. */
    public Component message(String key, Map<String, String> placeholders) {
        ConfigurationSection entry = entry(key);
        if (!isEnabled(entry)) return null;
        String message = entry.getString("message");
        return message == null ? null : formatter.applyChat(prefix() + message, placeholders);
    }

    public Component messageWithoutPrefix(String key, Map<String, String> placeholders) {
        ConfigurationSection entry = entry(key);
        if (!isEnabled(entry)) return null;
        String message = entry.getString("message");
        return message == null ? null : formatter.applyChat(message, placeholders);
    }

    public String consoleMessage(String key, Map<String, String> placeholders) {
        ConfigurationSection entry = entry(key);
        if (!isEnabled(entry)) return null;
        String message = entry.getString("message");
        return message == null ? null : TextFormatter.toPlain(message, placeholders);
    }

    public Component title(String key, Map<String, String> placeholders) {
        return component(key, "title", placeholders);
    }

    public Component subtitle(String key, Map<String, String> placeholders) {
        return component(key, "subtitle", placeholders);
    }

    public Component actionBar(String key, Map<String, String> placeholders) {
        return component(key, "action_bar", placeholders);
    }

    public String sound(String key) {
        ConfigurationSection entry = entry(key);
        return isEnabled(entry) ? entry.getString("sound") : null;
    }

    public boolean contains(String key) {
        return entry(key) != null;
    }

    /**
     * A plain text under {@code command}, such as a boss bar line, which is not a message entry. Falls
     * back to {@code defaultValue} when no locale has it.
     */
    public Component commandText(String path, String defaultValue, Map<String, String> placeholders) {
        String fullPath = "command." + path;
        for (LocaleData locale = localeSupplier.get(); locale != null; locale = locale.fallback()) {
            String value = locale.messages().getString(fullPath);
            if (value != null) return formatter.apply(value, placeholders);
        }
        return formatter.apply(defaultValue, placeholders);
    }

    public Component commandText(String path, String defaultValue) {
        return commandText(path, defaultValue, EMPTY_PLACEHOLDERS);
    }

    private Component component(String key, String component, Map<String, String> placeholders) {
        ConfigurationSection entry = entry(key);
        if (!isEnabled(entry)) return null;
        String value = entry.getString(component);
        return value == null ? null : formatter.apply(value, placeholders);
    }

    private String prefix() {
        for (LocaleData locale = localeSupplier.get(); locale != null; locale = locale.fallback()) {
            String prefix = locale.messages().getString("prefix");
            if (prefix != null) return prefix;
        }
        return "";
    }

    private static boolean isEnabled(ConfigurationSection entry) {
        return entry != null && entry.getBoolean("enabled", true);
    }

    /** The message entry from the first locale that has it, trying an old key's new name last. */
    private ConfigurationSection entry(String key) {
        ConfigurationSection entry = lookup(key);
        if (entry != null) return entry;

        String alias = LegacyLanguageKeys.messageKey(key);
        if (alias == null || alias.equals(key)) return null;
        entry = lookup(alias);
        if (entry != null) legacyWarning.accept("Message key '" + key + "' was renamed to '" + alias + "'");
        return entry;
    }

    private ConfigurationSection lookup(String key) {
        for (LocaleData locale = localeSupplier.get(); locale != null; locale = locale.fallback()) {
            ConfigurationSection entry = locale.messages().getConfigurationSection(key);
            if (entry != null) return entry;
        }
        return null;
    }
}
