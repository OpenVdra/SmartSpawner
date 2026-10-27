package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.LanguageComponentFormatter;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.TextFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Spawner item text from {@code items.yml}. Only the server locale's file is read, never a fallback:
 * a mob section deliberately leaves keys out to inherit them from {@code default}, and a fallback
 * would fill them from another file instead.
 */
public final class ItemLanguageSection {
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private final Supplier<LocaleData> localeSupplier;
    private final LanguageCache cache;
    private final PlaceholderFormatter formatter;
    private final Map<String, String> variantKeyCache = new ConcurrentHashMap<>();

    public ItemLanguageSection(Supplier<LocaleData> localeSupplier, LanguageCache cache, PlaceholderFormatter formatter) {
        this.localeSupplier = localeSupplier;
        this.cache = cache;
        this.formatter = formatter;
    }

    /** A material's name from {@code item.<MATERIAL>.name}, or its formatted ID, as a section-sign string for placeholders. */
    public String vanillaName(Material material) {
        if (material == null) return "Unknown Item";

        String cacheKey = "material|" + material.name();
        String cached = cache.getCachedMaterialName(cacheKey);
        if (cached != null) return cached;

        String name = items().getString("item." + material.name() + ".name");
        name = name == null
                ? formatter.formatEnumName(material.name())
                : TextFormatter.toLegacy(formatter.apply(name, EMPTY_PLACEHOLDERS));

        cache.putCachedMaterialName(cacheKey, name);
        return name;
    }

    public List<Component> vanillaLore(Material material) {
        return material == null ? List.of() : lore("item." + material.name() + ".lore");
    }

    public Component name(String key) {
        return name(key, EMPTY_PLACEHOLDERS);
    }

    public Component name(String key, Map<String, String> placeholders) {
        String name = items().getString(key);
        return TextFormatter.noItalic(name == null ? Component.text(key) : formatter.apply(name, placeholders));
    }

    public String variantKey(String section, String variant, String field) {
        String cacheKey = section + "|" + variant + "|" + field;
        return variantKeyCache.computeIfAbsent(cacheKey, ignored -> resolveVariantKey(section, variant, field));
    }

    public void clearCache() {
        variantKeyCache.clear();
    }

    private String resolveVariantKey(String section, String variant, String field) {
        String variantKey = section + "." + variant + "." + field;
        if (items().contains(variantKey)) {
            return variantKey;
        }

        String defaultKey = section + ".default." + field;
        if (items().contains(defaultKey)) {
            return defaultKey;
        }

        return section + "." + field;
    }

    public List<Component> lore(String key) {
        return lore(key, EMPTY_PLACEHOLDERS);
    }

    public List<Component> lore(String key, Map<String, String> placeholders) {
        return items().getStringList(key).stream()
                .map(line -> TextFormatter.noItalic(formatter.apply(line, placeholders)))
                .toList();
    }

    public Component translatableLootLine(String templateKey, Material material, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(items().getString(templateKey), material, amount, chance);
    }

    public Component translatableLootLine(String templateKey, Component itemName, String amount, String chance) {
        return LanguageComponentFormatter.translatableLootLine(items().getString(templateKey), itemName, amount, chance);
    }

    /** A lore with {@code {loot_items}} expanded to the given lines, or to {@code emptyLootKey}'s text when there are none. */
    public List<Component> loreComponents(String key, Map<String, String> placeholders,
                                          List<Component> lootItemComponents, String emptyLootKey) {
        List<Component> loot = lootItemComponents;
        if (loot.isEmpty()) {
            String empty = items().getString(emptyLootKey, "");
            loot = empty.isEmpty() ? List.of() : List.of(formatter.apply(empty, EMPTY_PLACEHOLDERS));
        }
        return LanguageComponentFormatter.lore(items().getStringList(key), placeholders,
                Map.of("loot_items", loot), line -> formatter.apply(line, placeholders));
    }

    private YamlConfiguration items() {
        return localeSupplier.get().items();
    }
}
