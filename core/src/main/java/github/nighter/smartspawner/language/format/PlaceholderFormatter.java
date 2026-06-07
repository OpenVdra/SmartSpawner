package github.nighter.smartspawner.language.format;

import github.nighter.smartspawner.language.cache.LanguageCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlaceholderFormatter {
    private final LanguageCache cache;
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;

    public PlaceholderFormatter(LanguageCache cache, AtomicInteger cacheHits, AtomicInteger cacheMisses) {
        this.cache = cache;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public String apply(String text, Map<String, String> placeholders) {
        if (text == null) {
            return null;
        }

        String key = cacheKey(text, placeholders);
        String cached = cache.getCachedString(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        String result = replacePlaceholders(text, placeholders);
        result = ColorUtil.translateHexColorCodes(result);
        cache.putCachedString(key, result);
        return result;
    }

    public String applyOnlyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) {
            return null;
        }

        String key = cacheKey(text, placeholders);
        String cached = cache.getCachedString(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        String result = replacePlaceholders(text, placeholders);
        cache.putCachedString(key, result);
        return result;
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
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

    public List<String> processMultilinePlaceholders(String line, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        String multilineKey = findMultilinePlaceholder(line, placeholders);

        if (multilineKey == null) {
            result.add(apply(line, placeholders));
            return result;
        }

        String multilineValue = placeholders.get(multilineKey);
        String placeholder = "{" + multilineKey + "}";
        String processedLine = replacePlaceholders(line, placeholders);
        String[] valueLines = multilineValue.split("\n");
        String lineStart = processedLine.substring(0, processedLine.indexOf(placeholder));

        result.add(ColorUtil.translateHexColorCodes(processedLine.replace(placeholder, valueLines[0])));
        for (int i = 1; i < valueLines.length; i++) {
            result.add(ColorUtil.translateHexColorCodes(lineStart + valueLines[i]));
        }
        return result;
    }

    private String findMultilinePlaceholder(String line, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.contains("\n") && line.contains("{" + entry.getKey() + "}")) {
                return entry.getKey();
            }
        }
        return null;
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
