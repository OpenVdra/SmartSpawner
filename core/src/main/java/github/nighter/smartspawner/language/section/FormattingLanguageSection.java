package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.cache.LanguageCache;
import github.nighter.smartspawner.language.file.LocaleData;
import github.nighter.smartspawner.language.format.NumberFormatter;
import github.nighter.smartspawner.language.format.PlaceholderFormatter;
import github.nighter.smartspawner.language.format.TextFormatter;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.function.Supplier;

/** Number formats and mob names from {@code formatting.yml}. */
public final class FormattingLanguageSection {
    private final Supplier<LocaleData> localeSupplier;
    private final LanguageCache cache;
    private final PlaceholderFormatter formatter;

    public FormattingLanguageSection(Supplier<LocaleData> localeSupplier, LanguageCache cache,
                                     PlaceholderFormatter formatter) {
        this.localeSupplier = localeSupplier;
        this.cache = cache;
        this.formatter = formatter;
    }

    public String number(double number) {
        return new NumberFormatter(localeSupplier.get().formatting()).format(number);
    }

    /** The mob's name as a section-sign string, ready to be a placeholder value. */
    public String mobName(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) return "Unknown";

        String cacheKey = "mob_name|" + type.name();
        String cached = cache.getCachedEntityName(cacheKey);
        if (cached != null) return cached;

        String result = localeSupplier.get().formatting().getString("mob_names." + type.name());
        result = result == null
                ? formatter.formatEnumName(type.name())
                : TextFormatter.toLegacy(formatter.apply(result, Collections.emptyMap()));
        cache.putCachedEntityName(cacheKey, result);
        return result;
    }
}
