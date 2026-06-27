package github.nighter.smartspawner.language;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LanguageFileType;
import github.nighter.smartspawner.language.file.LanguageRepository;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.SmallCapsFormatter;
import github.nighter.smartspawner.language.section.FormattingLanguageSection;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
import github.nighter.smartspawner.language.section.HologramLanguageSection;
import github.nighter.smartspawner.language.section.ItemLanguageSection;
import github.nighter.smartspawner.language.section.MessageLanguageSection;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LanguageManager {
    private final LanguageRepository repository;
    private final LanguageCache cache;
    private final PlaceholderFormatter placeholderFormatter;
    private final SmallCapsFormatter smallCapsFormatter;
    private final MessageLanguageSection messages;
    private final GuiLanguageSection guiLanguage;
    private final GuiLanguageSection commandGuiLanguage;
    private final ItemLanguageSection items;
    private final FormattingLanguageSection formatting;
    private final HologramLanguageSection hologram;
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger cacheMisses = new AtomicInteger();

    public LanguageManager(JavaPlugin plugin) {
        this(plugin, LanguageFileType.values());
    }

    public LanguageManager(SmartSpawner plugin, LanguageFileType... fileTypes) {
        this((JavaPlugin) plugin, fileTypes);
    }

    private LanguageManager(JavaPlugin plugin, LanguageFileType... fileTypes) {
        repository = new LanguageRepository(plugin, fileTypes);
        repository.initialize();

        cache = new LanguageCache(cacheHits, cacheMisses);
        placeholderFormatter = new PlaceholderFormatter(cache, cacheHits, cacheMisses);
        smallCapsFormatter = new SmallCapsFormatter(cache, cacheHits, cacheMisses);
        messages = new MessageLanguageSection(
                repository::current,
                placeholderFormatter::apply,
                placeholderFormatter::applyOnlyPlaceholders
        );
        guiLanguage = createGuiSection("GUI", LanguageFileType.GUI);
        commandGuiLanguage = createGuiSection("command GUI", LanguageFileType.COMMAND_GUI);
        items = new ItemLanguageSection(
                repository::current,
                () -> repository.isActive(LanguageFileType.ITEMS),
                cache,
                placeholderFormatter,
                placeholderFormatter::apply
        );
        formatting = new FormattingLanguageSection(
                repository::current,
                () -> repository.isActive(LanguageFileType.FORMATTING),
                cache,
                placeholderFormatter,
                placeholderFormatter::apply
        );
        hologram = new HologramLanguageSection(plugin, repository::current);
    }

    private GuiLanguageSection createGuiSection(String label, LanguageFileType fileType) {
        return new GuiLanguageSection(
                label,
                () -> fileType == LanguageFileType.COMMAND_GUI
                        ? repository.current().commandGui()
                        : repository.current().gui(),
                () -> repository.isActive(fileType),
                placeholderFormatter::apply,
                1000,
                250,
                250
        );
    }

    public String getDefaultLocale() {
        return repository.defaultLocale();
    }

    public void loadLanguages() {
        repository.loadLanguages();
    }

    public void loadLanguages(LanguageFileType... fileTypes) {
        repository.loadLanguages(fileTypes);
    }

    public void reloadLanguages() {
        clearCache();
        repository.reload();
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        return messages.message(key, placeholders);
    }

    public String getMessageWithoutPrefix(String key, Map<String, String> placeholders) {
        return messages.messageWithoutPrefix(key, placeholders);
    }

    public String getMessageForConsole(String key, Map<String, String> placeholders) {
        return messages.consoleMessage(key, placeholders);
    }

    public String getTitle(String key, Map<String, String> placeholders) {
        return messages.title(key, placeholders);
    }

    public String getSubtitle(String key, Map<String, String> placeholders) {
        return messages.subtitle(key, placeholders);
    }

    public String getActionBar(String key, Map<String, String> placeholders) {
        return messages.actionBar(key, placeholders);
    }

    public String getSound(String key) {
        return messages.sound(key);
    }

    String getRawMessage(String path, Map<String, String> placeholders) {
        return messages.raw(path, placeholders);
    }

    public boolean keyExists(String key) {
        return messages.contains(key);
    }

    public String getCommandConfig(String path, String defaultValue) {
        return messages.commandConfig(path, defaultValue);
    }

    public String getCommandConfig(String path, String defaultValue, Map<String, String> placeholders) {
        return messages.commandConfig(path, defaultValue, placeholders);
    }

    public GuiLanguageSection gui() {
        return guiLanguage;
    }

    public GuiLanguageSection commandGui() {
        return commandGuiLanguage;
    }

    public String getGuiTitle(String key) {
        return guiLanguage.title(key);
    }

    public String getGuiTitle(String key, Map<String, String> placeholders) {
        return guiLanguage.title(key, placeholders);
    }

    public String getGuiConfigString(String key, String defaultValue) {
        return guiLanguage.configString(key, defaultValue);
    }

    public String getGuiItemName(String key) {
        return guiLanguage.name(key);
    }

    public String getGuiItemName(String key, Map<String, String> placeholders) {
        return guiLanguage.name(key, placeholders);
    }

    public String[] getGuiItemLore(String key) {
        return guiLanguage.lore(key);
    }

    public String[] getGuiItemLore(String key, Map<String, String> placeholders) {
        return guiLanguage.lore(key, placeholders);
    }

    public List<String> getGuiItemLoreAsList(String key) {
        return guiLanguage.loreList(key);
    }

    public List<String> getGuiItemLoreAsList(String key, Map<String, String> placeholders) {
        return guiLanguage.loreList(key, placeholders);
    }

    public List<String> getGuiItemLoreWithMultilinePlaceholders(
            String key,
            Map<String, String> placeholders
    ) {
        return guiLanguage.loreWithMultilinePlaceholders(key, placeholders);
    }

    public String getVanillaItemName(Material material) {
        return items.vanillaName(material);
    }

    public String[] getVanillaItemLore(Material material) {
        return items.vanillaLore(material);
    }

    public String getItemName(String key) {
        return items.name(key);
    }

    public String getItemName(String key, Map<String, String> placeholders) {
        return items.name(key, placeholders);
    }

    public String getItemVariantKey(String section, String variant, String field) {
        return items.variantKey(section, variant, field);
    }

    public String[] getItemLore(String key) {
        return items.lore(key);
    }

    public String[] getItemLore(String key, Map<String, String> placeholders) {
        return items.lore(key, placeholders);
    }

    public List<String> getItemLoreWithMultilinePlaceholders(
            String key,
            Map<String, String> placeholders
    ) {
        return items.loreWithMultilinePlaceholders(key, placeholders);
    }

    public Component buildTranslatableLootLine(
            String templateKey,
            Material material,
            String amount,
            String chance
    ) {
        return items.translatableLootLine(templateKey, material, amount, chance);
    }

    public Component buildTranslatableGuiLootLine(
            String templateKey,
            Material material,
            String amount,
            String chance
    ) {
        return guiLanguage.translatableLootLine(templateKey, material, amount, chance);
    }

    public List<Component> buildItemLoreAsComponents(
            String key,
            Map<String, String> stringPlaceholders,
            List<Component> lootItemComponents,
            String emptyLootKey
    ) {
        return items.loreComponents(key, stringPlaceholders, lootItemComponents, emptyLootKey);
    }

    public List<Component> buildGuiLoreAsComponents(
            String key,
            Map<String, String> stringPlaceholders,
            List<Component> lootItemComponents,
            String emptyLootKey
    ) {
        return guiLanguage.loreComponents(key, stringPlaceholders, lootItemComponents, emptyLootKey);
    }

    public String formatNumber(double number) {
        return formatting.number(number);
    }

    public String getFormattedMobName(EntityType type) {
        return formatting.mobName(type);
    }

    public String getHologramText() {
        return hologram.text();
    }

    public void clearCache() {
        cache.clear();
        items.clearCache();
        guiLanguage.clearCache();
        commandGuiLanguage.clearCache();
    }

    public String applyPlaceholdersAndColors(String text, Map<String, String> placeholders) {
        return placeholderFormatter.apply(text, placeholders);
    }

    public String applyOnlyPlaceholders(String text, Map<String, String> placeholders) {
        return placeholderFormatter.applyOnlyPlaceholders(text, placeholders);
    }

    public String getSmallCaps(String text) {
        return smallCapsFormatter.apply(text);
    }

    public String formatEnumName(String enumName) {
        return placeholderFormatter.formatEnumName(enumName);
    }

    public String getColorCode(String path) {
        return guiLanguage.colorCode(path);
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("string_cache_size", cache.getStringCacheSize());
        stats.put("string_cache_capacity", cache.getStringCacheCapacity());
        stats.put("lore_cache_size", cache.getLoreCacheSize());
        stats.put("lore_cache_capacity", cache.getLoreCacheCapacity());
        stats.put("lore_list_cache_size", cache.getLoreListCacheSize());
        stats.put("lore_list_cache_capacity", cache.getLoreListCacheCapacity());
        guiLanguage.addCacheStats(stats, "gui");
        commandGuiLanguage.addCacheStats(stats, "command_gui");
        stats.put("entity_name_cache_size", cache.getEntityNameCacheSize());
        stats.put("entity_name_cache_capacity", cache.getEntityNameCacheCapacity());
        stats.put("small_caps_cache_size", cache.getSmallCapsCacheSize());
        stats.put("small_caps_cache_capacity", cache.getSmallCapsCacheCapacity());
        stats.put("material_name_cache_size", cache.getMaterialNameCacheSize());
        stats.put("material_name_cache_capacity", cache.getMaterialNameCacheCapacity());
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("hit_ratio", cacheHits.get() > 0
                ? (double) cacheHits.get() / (cacheHits.get() + cacheMisses.get())
                : 0);
        return stats;
    }
}
