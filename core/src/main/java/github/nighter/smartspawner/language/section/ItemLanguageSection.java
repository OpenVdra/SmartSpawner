package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.LanguageComponentFormatter;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class ItemLanguageSection {
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private final Supplier<LocaleData> localeSupplier;
    private final BooleanSupplier enabled;
    private final LanguageCache cache;
    private final PlaceholderFormatter placeholderFormatter;
    private final BiFunction<String, Map<String, String>, String> formatter;
    private final Map<String, String> variantKeyCache = new ConcurrentHashMap<>();

    public ItemLanguageSection(
            Supplier<LocaleData> localeSupplier,
            BooleanSupplier enabled,
            LanguageCache cache,
            PlaceholderFormatter placeholderFormatter,
            BiFunction<String, Map<String, String>, String> formatter
    ) {
        this.localeSupplier = localeSupplier;
        this.enabled = enabled;
        this.cache = cache;
        this.placeholderFormatter = placeholderFormatter;
        this.formatter = formatter;
    }

    public String vanillaName(Material material) {
        if (material == null) return "Unknown Item";

        String cacheKey = "material|" + material.name();
        String cached = cache.getCachedMaterialName(cacheKey);
        if (cached != null) return cached;

        String name = enabled.getAsBoolean()
                ? locale().items().getString("item." + material.name() + ".name")
                : null;
        name = name == null
                ? placeholderFormatter.formatEnumName(material.name())
                : formatter.apply(name, null);

        cache.putCachedMaterialName(cacheKey, name);
        return name;
    }

    public String[] vanillaLore(Material material) {
        return material == null ? new String[0] : lore("item." + material.name() + ".lore");
    }

    public String name(String key) {
        return name(key, EMPTY_PLACEHOLDERS);
    }

    public String name(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return key;
        String name = locale().items().getString(key);
        return name == null ? key : formatter.apply(name, placeholders);
    }

    public String variantKey(String section, String variant, String field) {
        if (!enabled.getAsBoolean()) return section + "." + field;

        String cacheKey = section + "|" + variant + "|" + field;
        return variantKeyCache.computeIfAbsent(cacheKey, ignored -> resolveVariantKey(section, variant, field));
    }

    public void clearCache() {
        variantKeyCache.clear();
    }

    private String resolveVariantKey(String section, String variant, String field) {
        String variantKey = section + "." + variant + "." + field;
        if (locale().items().contains(variantKey)) {
            return variantKey;
        }

        String defaultKey = section + ".default." + field;
        if (locale().items().contains(defaultKey)) {
            return defaultKey;
        }

        return section + "." + field;
    }

    public String[] lore(String key) {
        return lore(key, EMPTY_PLACEHOLDERS);
    }

    public String[] lore(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return new String[0];

        String cacheKey = placeholderFormatter.cacheKey(key, placeholders);
        String[] cached = cache.getCachedLore(cacheKey);
        if (cached != null) return cached;

        String[] result = locale().items().getStringList(key).stream()
                .map(line -> formatter.apply(line, placeholders))
                .toArray(String[]::new);
        cache.putCachedLore(cacheKey, result);
        return result;
    }

    public List<String> loreWithMultilinePlaceholders(String key, Map<String, String> placeholders) {
        if (!enabled.getAsBoolean()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (String line : locale().items().getStringList(key)) {
            result.addAll(placeholderFormatter.processMultilinePlaceholders(line, placeholders));
        }
        return result;
    }

    public Component translatableLootLine(String templateKey, Material material, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(
                locale().items().getString(templateKey),
                material,
                amount,
                chance
        );
    }

    public List<Component> loreComponents(
            String key,
            Map<String, String> placeholders,
            List<Component> lootItemComponents,
            String emptyLootKey
    ) {
        if (!enabled.getAsBoolean()) return Collections.emptyList();

        return LanguageComponentFormatter.loreComponents(
                locale().items().getStringList(key),
                line -> formatter.apply(line, placeholders),
                () -> locale().items().getString(emptyLootKey, ""),
                lootItemComponents
        );
    }

    private LocaleData locale() {
        return localeSupplier.get();
    }
}
