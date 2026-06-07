package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.file.LocaleData;

import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class MessageLanguageSection {
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private final Supplier<LocaleData> localeSupplier;
    private final BiFunction<String, Map<String, String>, String> formatter;
    private final BiFunction<String, Map<String, String>, String> placeholderFormatter;

    public MessageLanguageSection(
            Supplier<LocaleData> localeSupplier,
            BiFunction<String, Map<String, String>, String> formatter,
            BiFunction<String, Map<String, String>, String> placeholderFormatter
    ) {
        this.localeSupplier = localeSupplier;
        this.formatter = formatter;
        this.placeholderFormatter = placeholderFormatter;
    }

    public String message(String key, Map<String, String> placeholders) {
        if (!isEnabled(key)) return null;

        String message = resolve(key + ".message");
        if (message == null) return "Missing message: " + key;

        return formatter.apply(locale().messages().getString("prefix", "") + message, placeholders);
    }

    public String messageWithoutPrefix(String key, Map<String, String> placeholders) {
        if (!isEnabled(key)) return null;

        String message = resolve(key + ".message");
        return message == null ? "Missing message: " + key : formatter.apply(message, placeholders);
    }

    public String consoleMessage(String key, Map<String, String> placeholders) {
        if (!isEnabled(key)) return null;

        String message = resolve(key + ".message");
        return message == null ? "Missing message: " + key : placeholderFormatter.apply(message, placeholders);
    }

    public String title(String key, Map<String, String> placeholders) {
        return isEnabled(key) ? raw(key + ".title", placeholders) : null;
    }

    public String subtitle(String key, Map<String, String> placeholders) {
        return isEnabled(key) ? raw(key + ".subtitle", placeholders) : null;
    }

    public String actionBar(String key, Map<String, String> placeholders) {
        return isEnabled(key) ? raw(key + ".action_bar", placeholders) : null;
    }

    public String sound(String key) {
        return isEnabled(key) ? resolve(key + ".sound") : null;
    }

    public String raw(String path, Map<String, String> placeholders) {
        String message = resolve(path);
        return message == null ? null : formatter.apply(message, placeholders);
    }

    public boolean contains(String key) {
        return locale().commandMessages().contains(key) || locale().messages().contains(key);
    }

    public String commandConfig(String path, String defaultValue, Map<String, String> placeholders) {
        String value = locale().commandMessages().getString(path);
        return value == null ? defaultValue : formatter.apply(value, placeholders);
    }

    public String commandConfig(String path, String defaultValue) {
        return commandConfig(path, defaultValue, EMPTY_PLACEHOLDERS);
    }

    private String resolve(String path) {
        String value = locale().commandMessages().getString(path);
        return value != null ? value : locale().messages().getString(path);
    }

    private boolean isEnabled(String key) {
        if (!locale().commandMessages().contains(key)) {
            return locale().messages().getBoolean(key + ".enabled", true);
        }
        return locale().commandMessages().getBoolean(key + ".enabled", true);
    }

    private LocaleData locale() {
        return localeSupplier.get();
    }
}
