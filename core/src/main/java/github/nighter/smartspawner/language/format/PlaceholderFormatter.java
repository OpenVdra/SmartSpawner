package github.nighter.smartspawner.language.format;

import github.nighter.smartspawner.language.cache.LanguageCache;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@link TextFormatter} with a cache keyed by the text and its placeholders. */
public final class PlaceholderFormatter {
    private final LanguageCache cache;

    public PlaceholderFormatter(LanguageCache cache) {
        this.cache = cache;
    }

    /** Display text: item names and lore, titles, holograms. See {@link TextFormatter#parse}. */
    public Component apply(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        String key = cacheKey(text, placeholders);
        Component cached = cache.getText(key);
        if (cached != null) return cached;

        Component result = TextFormatter.parse(text, placeholders);
        cache.putText(key, result);
        return result;
    }

    /** A chat message, which may use click and hover. See {@link TextFormatter#parseChat}. */
    public Component applyChat(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        String key = cacheKey(text, placeholders);
        Component cached = cache.getChat(key);
        if (cached != null) return cached;

        Component result = TextFormatter.parseChat(text, placeholders);
        cache.putChat(key, result);
        return result;
    }

    /** Replaces {@code {key}} placeholders and nothing else. The result is still a template, not text. */
    public String applyOnlyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public String formatEnumName(String enumName) {
        String[] words = enumName.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(word.charAt(0))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    public String cacheKey(String key, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return key;

        StringBuilder builder = new StringBuilder(key);
        List<String> keys = new ArrayList<>(placeholders.keySet());
        Collections.sort(keys);
        for (String placeholderKey : keys) {
            builder.append('|').append(placeholderKey).append('=').append(placeholders.get(placeholderKey));
        }
        return builder.toString();
    }
}
