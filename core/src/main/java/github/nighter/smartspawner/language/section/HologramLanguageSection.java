package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.file.LocaleData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Supplier;

/** The hologram template: {@code hologram.lines} in {@code items.yml}, or the old {@code hologram.text} in config.yml. */
public final class HologramLanguageSection {
    private final JavaPlugin plugin;
    private final Supplier<LocaleData> localeSupplier;

    public HologramLanguageSection(JavaPlugin plugin, Supplier<LocaleData> localeSupplier) {
        this.plugin = plugin;
        this.localeSupplier = localeSupplier;
    }

    /** The unformatted lines, joined with newlines. */
    public String text() {
        YamlConfiguration items = localeSupplier.get().items();
        if (items.contains("hologram.lines")) {
            if (items.isList("hologram.lines")) return String.join("\n", items.getStringList("hologram.lines"));
            return items.getString("hologram.lines", "");
        }

        if (!plugin.getConfig().contains("hologram.text")) return "";

        List<String> lines = plugin.getConfig().getStringList("hologram.text");
        return lines.isEmpty()
                ? plugin.getConfig().getString("hologram.text", "")
                : String.join("\n", lines);
    }
}
