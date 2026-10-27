package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.LanguageComponentFormatter;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.TextFormatter;
import github.nighter.smartspawner.utils.LRUCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * GUI text from {@code gui.yml}. The spawner GUIs read its top level, the command GUIs its
 * {@code command} section ({@code keyPrefix}), so their keys are written without that prefix.
 *
 * <p>A text is looked up in the active layout's override files first ({@code texts_<locale>.yml},
 * then {@code texts.yml}), then down the locale chain. A button the owner has in their file owns its
 * lore: deleting {@code lore} there removes it instead of bringing back the next locale's.</p>
 *
 * <p>Names, lore and plain texts come back with italic turned off, as item text needs. Titles do not,
 * because inventory titles are never italic.</p>
 */
public final class GuiLanguageSection {
    private final String label;
    private final String keyPrefix;
    private final Supplier<LocaleData> localeSupplier;
    private final Supplier<List<YamlConfiguration>> overrideSupplier;
    private final Function<String, String> legacyAlias;
    private final Consumer<String> legacyWarning;
    private final PlaceholderFormatter formatter;
    private final LRUCache<String, Component> nameCache;
    private final LRUCache<String, List<Component>> loreCache;
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger cacheMisses = new AtomicInteger();

    public GuiLanguageSection(
            String label,
            String keyPrefix,
            Supplier<LocaleData> localeSupplier,
            Supplier<List<YamlConfiguration>> overrideSupplier,
            Function<String, String> legacyAlias,
            Consumer<String> legacyWarning,
            PlaceholderFormatter formatter,
            int nameCacheSize,
            int loreCacheSize
    ) {
        this.label = label;
        this.keyPrefix = keyPrefix;
        this.localeSupplier = localeSupplier;
        this.overrideSupplier = overrideSupplier;
        this.legacyAlias = legacyAlias;
        this.legacyWarning = legacyWarning;
        this.formatter = formatter;
        this.nameCache = new LRUCache<>(nameCacheSize);
        this.loreCache = new LRUCache<>(loreCacheSize);
    }

    public Component title(String key) {
        return title(key, Collections.emptyMap());
    }

    public Component title(String key, Map<String, String> placeholders) {
        String title = string(key);
        return title == null
                ? Component.text("Missing " + label + " title: " + key)
                : formatter.apply(title, placeholders);
    }

    /** A short text placed inside another one (a world or menu name), as a section-sign string for a placeholder value. */
    public String configString(String key, String defaultValue) {
        String value = string(key);
        return value == null || value.isBlank() ? defaultValue : TextFormatter.toLegacy(formatter.apply(value, Collections.emptyMap()));
    }

    public boolean contains(String key) {
        return string(key) != null || !stringList(key).isEmpty();
    }

    public Component name(String key) {
        return name(key, Collections.emptyMap());
    }

    /** An item name, or any other one-line text shown on an item. */
    public Component name(String key, Map<String, String> placeholders) {
        String cacheKey = formatter.cacheKey(key, placeholders);
        Component cached = nameCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        String name = string(key);
        if (name == null) return Component.text("Missing " + label + " item name: " + key);

        Component result = TextFormatter.noItalic(formatter.apply(name, placeholders));
        nameCache.put(cacheKey, result);
        return result;
    }

    public List<Component> lore(String key) {
        return lore(key, Collections.emptyMap());
    }

    public List<Component> lore(String key, Map<String, String> placeholders) {
        String cacheKey = formatter.cacheKey(key, placeholders);
        List<Component> cached = loreCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        List<Component> result = stringList(key).stream()
                .map(line -> TextFormatter.noItalic(formatter.apply(line, placeholders)))
                .toList();
        loreCache.put(cacheKey, result);
        return result;
    }

    /**
     * A lore where a line holding one of {@code expansions}' placeholders becomes one line per
     * component in that list, and disappears when the list is empty. Used for {@code {loot_items}},
     * {@code {available_items}} and the like. Uncached: the expansions are built per call.
     */
    public List<Component> lore(String key, Map<String, String> placeholders, Map<String, List<Component>> expansions) {
        return LanguageComponentFormatter.lore(stringList(key), placeholders, expansions,
                line -> formatter.apply(line, placeholders));
    }

    /** A lore where {@code {placeholderKey}} is a ready component (an item's effective name) rather than text. */
    public List<Component> loreWithItemName(String key, Map<String, String> placeholders,
                                            String placeholderKey, Component itemName) {
        return LanguageComponentFormatter.lore(stringList(key), placeholders, Map.of(),
                line -> TextFormatter.parse(line, placeholders, Map.of(placeholderKey, itemName)));
    }

    /**
     * A lore with {@code {loot_items}} expanded to the given lines, or to {@code emptyLootKey}'s text
     * when there are none.
     */
    public List<Component> loreComponents(String key, Map<String, String> placeholders,
                                          List<Component> lootItemComponents, String emptyLootKey) {
        List<Component> loot = lootItemComponents;
        if (loot.isEmpty()) {
            String empty = string(emptyLootKey);
            loot = empty == null || empty.isEmpty()
                    ? List.of()
                    : List.of(TextFormatter.noItalic(formatter.apply(empty, Collections.emptyMap())));
        }
        return lore(key, placeholders, Map.of("loot_items", loot));
    }

    public Component translatableLootLine(String templateKey, Material material, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(string(templateKey), material, amount, chance);
    }

    public Component translatableLootLine(String templateKey, Component itemName, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(string(templateKey), itemName, amount, chance);
    }

    /** The text as written in the file, before any formatting. For checking which placeholders it uses. */
    public String raw(String key) {
        String value = string(key);
        return value == null ? "" : value;
    }

    public List<String> rawList(String key) {
        return stringList(key);
    }

    public void clearCache() {
        nameCache.clear();
        loreCache.clear();
    }

    public void addCacheStats(Map<String, Object> stats, String prefix) {
        stats.put(prefix + "_name_cache_size", nameCache.size());
        stats.put(prefix + "_name_cache_capacity", nameCache.capacity());
        stats.put(prefix + "_lore_cache_size", loreCache.size());
        stats.put(prefix + "_lore_cache_capacity", loreCache.capacity());
        stats.put(prefix + "_cache_hits", cacheHits.get());
        stats.put(prefix + "_cache_misses", cacheMisses.get());
    }

    /**
     * A text from the layout's override files when they have it, otherwise from the first locale that
     * does. A key an override sets to an empty string still wins, so a layout can blank out a line.
     */
    private String string(String key) {
        String value = lookupString(keyPrefix + key);
        if (value != null) return value;
        String alias = alias(key);
        return alias == null ? null : lookupString(keyPrefix + alias);
    }

    private List<String> stringList(String key) {
        List<String> value = lookupList(keyPrefix + key);
        if (value != null) return value;
        String alias = alias(key);
        if (alias != null) {
            value = lookupList(keyPrefix + alias);
            if (value != null) return value;
        }
        return Collections.emptyList();
    }

    private String lookupString(String path) {
        for (YamlConfiguration override : overrideSupplier.get()) {
            if (override.contains(path) && !override.isConfigurationSection(path)) {
                return override.getString(path);
            }
        }
        for (LocaleData locale = localeSupplier.get(); locale != null; locale = locale.fallback()) {
            YamlConfiguration gui = locale.gui();
            if (gui.contains(path) && !gui.isConfigurationSection(path)) {
                return gui.getString(path);
            }
        }
        return null;
    }

    /** {@code null} when no file has the list; empty when a file has the button but not its list. */
    private List<String> lookupList(String path) {
        for (YamlConfiguration override : overrideSupplier.get()) {
            List<String> list = list(override, path);
            if (list != null) return list;
        }
        int dot = path.lastIndexOf('.');
        String parent = dot > 0 ? path.substring(0, dot) : null;
        for (LocaleData locale = localeSupplier.get(); locale != null; locale = locale.fallback()) {
            YamlConfiguration gui = locale.gui();
            List<String> list = list(gui, path);
            if (list != null) return list;
            // The owner has this button but removed the list: that is their choice, not a gap to fill.
            if (parent != null && parent.contains(".") && gui.isConfigurationSection(parent)) {
                return Collections.emptyList();
            }
        }
        return null;
    }

    private static List<String> list(YamlConfiguration config, String path) {
        if (config.isList(path)) return config.getStringList(path);
        if (config.isString(path)) return List.of(Objects.requireNonNull(config.getString(path)));
        return null;
    }

    private String alias(String key) {
        if (legacyAlias == null) return null;
        String alias = legacyAlias.apply(key);
        if (alias == null || alias.equals(key)) return null;
        legacyWarning.accept(label + " key '" + key + "' was renamed to '" + alias + "'");
        return alias;
    }
}
