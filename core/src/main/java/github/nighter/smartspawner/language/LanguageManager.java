package github.nighter.smartspawner.language;

import github.nighter.smartspawner.language.file.LanguageRepository;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every player-visible text. The server locale ({@code language}) is used for everything shared:
 * spawner items, holograms, the console. With {@code language_auto_detect} on, messages and GUIs a
 * single player sees use that player's client language when a locale folder for it exists, through
 * {@link #texts(CommandSender)}, {@link #gui(CommandSender)} and {@link #commandGui(CommandSender)}.
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final LanguageRepository repository;
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger cacheMisses = new AtomicInteger();
    private final Set<String> warnedLegacyKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, LocaleTexts> playerTexts = new ConcurrentHashMap<>();
    private final LocaleTexts server;
    /** The active layout's texts.yml under "", and each texts_<locale>.yml under its locale. */
    private volatile Map<String, YamlConfiguration> layoutTexts = Map.of();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        repository = new LanguageRepository(plugin);
        repository.initialize();
        server = newTexts(repository::server, () -> repository.serverLocale());
    }

    private LocaleTexts newTexts(java.util.function.Supplier<LocaleData> locale, java.util.function.Supplier<String> name) {
        return new LocaleTexts(plugin, locale, () -> layoutTextsFor(name.get()), this::warnLegacyKey,
                cacheHits, cacheMisses);
    }

    private List<YamlConfiguration> layoutTextsFor(String locale) {
        Map<String, YamlConfiguration> texts = layoutTexts;
        if (texts.isEmpty()) return List.of();
        List<YamlConfiguration> result = new ArrayList<>(2);
        YamlConfiguration own = texts.get(locale);
        if (own != null) result.add(own);
        YamlConfiguration shared = texts.get("");
        if (shared != null) result.add(shared);
        return result;
    }

    private void warnLegacyKey(String message) {
        if (warnedLegacyKeys.add(message)) {
            plugin.getLogger().warning(message + ". The old name still works in this version; "
                    + "update the code or addon that asks for it.");
        }
    }

    public String getDefaultLocale() {
        return repository.serverLocale();
    }

    public void reloadLanguages() {
        repository.reload();
        playerTexts.clear();
        clearCache();
    }

    /**
     * Loads the active layout's text overrides for the spawner GUIs: {@code texts.yml}, and any
     * {@code texts_<locale>.yml} next to it for players in that locale. {@code GuiLayoutConfig} calls
     * this whenever it loads a layout, so both reload paths pick it up.
     */
    public void loadLayoutTexts(File file) {
        Map<String, YamlConfiguration> texts = new HashMap<>();
        if (file.isFile()) texts.put("", YamlConfiguration.loadConfiguration(file));
        File[] localized = file.getParentFile() == null ? null
                : file.getParentFile().listFiles((dir, name) -> name.startsWith("texts_") && name.endsWith(".yml"));
        if (localized != null) {
            for (File localeFile : localized) {
                String locale = localeFile.getName().substring("texts_".length(), localeFile.getName().length() - 4);
                texts.put(locale, YamlConfiguration.loadConfiguration(localeFile));
            }
        }
        layoutTexts = Map.copyOf(texts);
        server.getGui().clearCache();
        playerTexts.values().forEach(t -> t.getGui().clearCache());
    }

    /** The texts a sender should see: their own language for a player when auto-detect is on, otherwise the server's. */
    public LocaleTexts texts(CommandSender viewer) {
        String locale = viewerLocale(viewer);
        if (locale == null) return server;
        return playerTexts.computeIfAbsent(locale, name -> newTexts(() -> {
            LocaleData data = repository.forLocale(name);
            return data != null ? data : repository.server();
        }, () -> name));
    }

    /** The locale a viewer sees, for keying caches of built items. Empty for the server language. */
    public String localeKey(CommandSender viewer) {
        String locale = viewerLocale(viewer);
        return locale == null ? "" : locale;
    }

    /** The viewer's own locale when it differs from the server's and can be loaded, otherwise {@code null}. */
    private String viewerLocale(CommandSender viewer) {
        if (!repository.autoDetect() || !(viewer instanceof Player player)) return null;
        String locale = repository.match(player.locale());
        if (locale == null || locale.equals(repository.serverLocale())) return null;
        return repository.forLocale(locale) == null ? null : locale;
    }

    public boolean isAutoDetect() {
        return repository.autoDetect();
    }

    public Set<String> getAvailableLocales() {
        return repository.availableLocales();
    }

    // ---- messages (server language; MessageService picks the player's own) ----

    public Component getMessage(String key, Map<String, String> placeholders) {
        return server.getMessages().message(key, placeholders);
    }

    public String getMessageForConsole(String key, Map<String, String> placeholders) {
        return server.getMessages().consoleMessage(key, placeholders);
    }

    public String getSound(String key) {
        return server.getMessages().sound(key);
    }

    public boolean keyExists(String key) {
        return server.getMessages().contains(key);
    }

    public Component getCommandText(String path, String defaultValue) {
        return server.getMessages().commandText(path, defaultValue);
    }

    public Component getCommandText(String path, String defaultValue, Map<String, String> placeholders) {
        return server.getMessages().commandText(path, defaultValue, placeholders);
    }

    // ---- GUIs ----

    public GuiLanguageSection gui() {
        return server.getGui();
    }

    public GuiLanguageSection gui(CommandSender viewer) {
        return texts(viewer).getGui();
    }

    public GuiLanguageSection commandGui() {
        return server.getCommandGui();
    }

    public GuiLanguageSection commandGui(CommandSender viewer) {
        return texts(viewer).getCommandGui();
    }

    // ---- items (always the server language: an item outlives whoever first saw it) ----

    public String getVanillaItemName(Material material) {
        return server.getItems().vanillaName(material);
    }

    public List<Component> getVanillaItemLore(Material material) {
        return server.getItems().vanillaLore(material);
    }

    public Component getItemName(String key, Map<String, String> placeholders) {
        return server.getItems().name(key, placeholders);
    }

    /** The text at {@code key} in items.yml as written, or {@code null} when the file has none. */
    public String getItemText(String key) {
        return repository.server().items().getString(key);
    }

    public String getItemVariantKey(String section, String variant, String field) {
        return server.getItems().variantKey(section, variant, field);
    }

    public List<Component> getItemLore(String key, Map<String, String> placeholders) {
        return server.getItems().lore(key, placeholders);
    }

    public Component buildTranslatableLootLine(String templateKey, Material material, String amount, String chance) {
        return server.getItems().translatableLootLine(templateKey, material, amount, chance);
    }

    public Component buildTranslatableLootLine(String templateKey, ItemStack item, String amount, String chance) {
        return server.getItems().translatableLootLine(templateKey, getItemDisplayName(item), amount, chance);
    }

    public List<Component> buildItemLoreAsComponents(String key, Map<String, String> stringPlaceholders,
                                                     List<Component> lootItemComponents, String emptyLootKey) {
        return server.getItems().loreComponents(key, stringPlaceholders, lootItemComponents, emptyLootKey);
    }

    /**
     * The name to show for this item: its custom name if it has one, otherwise the effective name the
     * client would display (which keeps component-specific names like potion effects).
     */
    public Component getItemDisplayName(ItemStack item) {
        if (item == null) return Component.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component custom = meta.displayName();
            if (custom != null) return custom;
        }
        return item.effectiveName();
    }

    // ---- formatting and holograms (server language) ----

    public String formatNumber(double number) {
        return server.getFormatting().number(number);
    }

    public String getFormattedMobName(EntityType type) {
        return server.getFormatting().mobName(type);
    }

    public String getHologramText() {
        return server.getHologram().text();
    }

    public String getSmallCaps(String text) {
        return server.getSmallCapsFormatter().apply(text);
    }

    public String formatEnumName(String enumName) {
        return server.getPlaceholderFormatter().formatEnumName(enumName);
    }

    public String applyOnlyPlaceholders(String text, Map<String, String> placeholders) {
        return server.getPlaceholderFormatter().applyOnlyPlaceholders(text, placeholders);
    }

    public void clearCache() {
        server.clearCache();
        playerTexts.values().forEach(LocaleTexts::clearCache);
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        server.addCacheStats(stats, "");
        playerTexts.forEach((locale, texts) -> texts.addCacheStats(stats, locale + "_"));
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("hit_ratio", cacheHits.get() > 0
                ? (double) cacheHits.get() / (cacheHits.get() + cacheMisses.get())
                : 0);
        return stats;
    }
}
