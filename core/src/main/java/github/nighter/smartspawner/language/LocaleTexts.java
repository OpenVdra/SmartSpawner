package github.nighter.smartspawner.language;

import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LegacyLanguageKeys;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.SmallCapsFormatter;
import github.nighter.smartspawner.language.section.FormattingLanguageSection;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
import github.nighter.smartspawner.language.section.HologramLanguageSection;
import github.nighter.smartspawner.language.section.ItemLanguageSection;
import github.nighter.smartspawner.language.section.MessageLanguageSection;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Every text section for one locale, with that locale's caches. */
@Getter
public final class LocaleTexts {
    private final LanguageCache cache;
    private final PlaceholderFormatter placeholderFormatter;
    private final SmallCapsFormatter smallCapsFormatter;
    private final MessageLanguageSection messages;
    private final GuiLanguageSection gui;
    private final GuiLanguageSection commandGui;
    private final ItemLanguageSection items;
    private final FormattingLanguageSection formatting;
    private final HologramLanguageSection hologram;

    LocaleTexts(JavaPlugin plugin, Supplier<LocaleData> locale, Supplier<List<YamlConfiguration>> layoutTexts,
                Consumer<String> legacyWarning, java.util.concurrent.atomic.AtomicInteger hits,
                java.util.concurrent.atomic.AtomicInteger misses) {
        cache = new LanguageCache(hits, misses);
        placeholderFormatter = new PlaceholderFormatter(cache);
        smallCapsFormatter = new SmallCapsFormatter(cache);
        messages = new MessageLanguageSection(locale, placeholderFormatter, legacyWarning);
        gui = new GuiLanguageSection("GUI", "", locale, layoutTexts,
                LegacyLanguageKeys::guiKey, legacyWarning, placeholderFormatter, 1000, 250);
        commandGui = new GuiLanguageSection("command GUI", "command.", locale, List::of,
                LegacyLanguageKeys::commandGuiKey, legacyWarning, placeholderFormatter, 500, 250);
        items = new ItemLanguageSection(locale, cache, placeholderFormatter);
        formatting = new FormattingLanguageSection(locale, cache, placeholderFormatter);
        hologram = new HologramLanguageSection(plugin, locale);
    }

    void clearCache() {
        cache.clear();
        items.clearCache();
        gui.clearCache();
        commandGui.clearCache();
    }

    void addCacheStats(Map<String, Object> stats, String prefix) {
        cache.addStats(stats, prefix);
        gui.addCacheStats(stats, prefix + "gui");
        commandGui.addCacheStats(stats, prefix + "command_gui");
    }
}
