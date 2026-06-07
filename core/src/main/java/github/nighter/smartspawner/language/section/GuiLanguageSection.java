package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.cache.LRUCache;
import github.nighter.smartspawner.language.format.ColorUtil;
import github.nighter.smartspawner.language.format.LanguageComponentFormatter;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GuiLanguageSection {
    private final String label;
    private final Supplier<YamlConfiguration> configSupplier;
    private final BooleanSupplier enabled;
    private final BiFunction<String, Map<String, String>, String> formatter;
    private final LRUCache<String, String> nameCache;
    private final LRUCache<String, String[]> loreCache;
    private final LRUCache<String, List<String>> loreListCache;
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger cacheMisses = new AtomicInteger();

    public GuiLanguageSection(
            String label,
            Supplier<YamlConfiguration> configSupplier,
            BooleanSupplier enabled,
            BiFunction<String, Map<String, String>, String> formatter,
            int stringCacheSize,
            int loreCacheSize,
            int loreListCacheSize
    ) {
        this.label = label;
        this.configSupplier = configSupplier;
        this.enabled = enabled;
        this.formatter = formatter;
        this.nameCache = new LRUCache<>(stringCacheSize);
        this.loreCache = new LRUCache<>(loreCacheSize);
        this.loreListCache = new LRUCache<>(loreListCacheSize);
    }

    public String title(String key) {
        return title(key, Collections.emptyMap());
    }

    public String title(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return null;
        String title = config().getString(key);
        return title == null ? "Missing " + label + " title: " + key : formatter.apply(title, placeholders);
    }

    public String configString(String key, String defaultValue) {
        if (!enabled.getAsBoolean()) return defaultValue;
        String value = config().getString(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public String name(String key) {
        return name(key, Collections.emptyMap());
    }

    public String name(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return null;

        String cacheKey = cacheKey(key, placeholders);
        String cached = nameCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        String name = config().getString(key);
        if (name == null) return "Missing " + label + " item name: " + key;

        String result = formatter.apply(name, placeholders);
        nameCache.put(cacheKey, result);
        return result;
    }

    public String[] lore(String key) {
        return lore(key, Collections.emptyMap());
    }

    public String[] lore(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return new String[0];

        String cacheKey = cacheKey(key, placeholders);
        String[] cached = loreCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        String[] result = config().getStringList(key).stream()
                .map(line -> formatter.apply(line, placeholders))
                .toArray(String[]::new);
        loreCache.put(cacheKey, result);
        return result;
    }

    public List<String> loreList(String key) {
        return loreList(key, Collections.emptyMap());
    }

    public List<String> loreList(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return Collections.emptyList();

        String cacheKey = cacheKey(key, placeholders);
        List<String> cached = loreListCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        List<String> result = config().getStringList(key).stream()
                .map(line -> formatter.apply(line, placeholders))
                .toList();
        loreListCache.put(cacheKey, result);
        return result;
    }

    public List<String> loreWithMultilinePlaceholders(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (String line : config().getStringList(key)) {
            String multilineKey = findMultilinePlaceholder(line, placeholders);
            if (multilineKey == null) {
                result.add(formatter.apply(line, placeholders));
                continue;
            }

            Map<String, String> singleLinePlaceholders = new HashMap<>(placeholders);
            String multilineValue = singleLinePlaceholders.remove(multilineKey);
            String processedLine = replacePlaceholders(line, singleLinePlaceholders);
            String placeholder = "{" + multilineKey + "}";
            String lineStart = processedLine.substring(0, processedLine.indexOf(placeholder));
            String[] valueLines = multilineValue.split("\n");

            result.add(ColorUtil.translateHexColorCodes(processedLine.replace(placeholder, valueLines[0])));
            for (int i = 1; i < valueLines.length; i++) {
                result.add(ColorUtil.translateHexColorCodes(lineStart + valueLines[i]));
            }
        }
        return result;
    }

    public Component translatableLootLine(String templateKey, Material material, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(config().getString(templateKey), material, amount, chance);
    }

    public List<Component> loreComponents(
            String key,
            Map<String, String> placeholders,
            List<Component> lootItemComponents,
            String emptyLootKey
    ) {
        if (!enabled.getAsBoolean()) return Collections.emptyList();

        return LanguageComponentFormatter.loreComponents(
                config().getStringList(key),
                line -> formatter.apply(line, placeholders),
                () -> config().getString(emptyLootKey, ""),
                lootItemComponents
        );
    }

    public String colorCode(String path) {
        if (!enabled.getAsBoolean()) return ChatColor.WHITE.toString();
        String color = config().getString(path);
        return color == null ? ChatColor.WHITE.toString() : formatter.apply(color, Collections.emptyMap());
    }

    public void clearCache() {
        nameCache.clear();
        loreCache.clear();
        loreListCache.clear();
    }

    public void addCacheStats(Map<String, Object> stats, String prefix) {
        stats.put(prefix + "_name_cache_size", nameCache.size());
        stats.put(prefix + "_name_cache_capacity", nameCache.capacity());
        stats.put(prefix + "_lore_cache_size", loreCache.size());
        stats.put(prefix + "_lore_cache_capacity", loreCache.capacity());
        stats.put(prefix + "_lore_list_cache_size", loreListCache.size());
        stats.put(prefix + "_lore_list_cache_capacity", loreListCache.capacity());
        stats.put(prefix + "_cache_hits", cacheHits.get());
        stats.put(prefix + "_cache_misses", cacheMisses.get());
    }

    private YamlConfiguration config() {
        YamlConfiguration config = configSupplier.get();
        return config == null ? new YamlConfiguration() : config;
    }

    private String cacheKey(String key, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return key;

        StringBuilder builder = new StringBuilder(key);
        List<String> keys = new ArrayList<>(placeholders.keySet());
        Collections.sort(keys);
        for (String placeholderKey : keys) {
            builder.append('|').append(placeholderKey).append('=').append(placeholders.get(placeholderKey));
        }
        return builder.toString();
    }

    private String findMultilinePlaceholder(String line, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return null;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.contains("\n") && line.contains("{" + entry.getKey() + "}")) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String replacePlaceholders(String line, Map<String, String> placeholders) {
        String result = line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
