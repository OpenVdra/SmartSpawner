package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.NumberFormatter;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class FormattingLanguageSection {
    private final Supplier<LocaleData> localeSupplier;
    private final BooleanSupplier enabled;
    private final LanguageCache cache;
    private final PlaceholderFormatter placeholderFormatter;
    private final BiFunction<String, Map<String, String>, String> formatter;

    public FormattingLanguageSection(
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

    public String number(double number) {
        YamlConfiguration config = enabled.getAsBoolean()
                ? localeSupplier.get().formatting()
                : new YamlConfiguration();
        return new NumberFormatter(config).format(number);
    }

    public String mobName(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) return "Unknown";

        String cacheKey = "mob_name|" + type.name();
        String cached = cache.getCachedEntityName(cacheKey);
        if (cached != null) return cached;

        String result = null;
        if (enabled.getAsBoolean()) {
            result = localeSupplier.get().formatting().getString("mob_names." + type.name());
            if (result != null) {
                result = formatter.apply(result, null);
            }
        }

        if (result == null) {
            result = placeholderFormatter.formatEnumName(type.name());
        }
        cache.putCachedEntityName(cacheKey, result);
        return result;
    }
}
