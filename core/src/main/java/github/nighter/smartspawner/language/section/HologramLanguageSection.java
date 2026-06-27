package github.nighter.smartspawner.language.section;

import github.nighter.smartspawner.language.file.LocaleData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Supplier;

public final class HologramLanguageSection {
    private final JavaPlugin plugin;
    private final Supplier<LocaleData> localeSupplier;

    public HologramLanguageSection(JavaPlugin plugin, Supplier<LocaleData> localeSupplier) {
        this.plugin = plugin;
        this.localeSupplier = localeSupplier;
    }

    public String text() {
        YamlConfiguration hologram = localeSupplier.get().hologram();
        if (hologram.contains("hologram_text")) {
            List<String> lines = hologram.getStringList("hologram_text");
            if (!lines.isEmpty()) return String.join("\n", lines);

            String singleLine = hologram.getString("hologram_text");
            if (singleLine != null) return singleLine;
        }

        if (!plugin.getConfig().contains("hologram.text")) return "";

        List<String> lines = plugin.getConfig().getStringList("hologram.text");
        return lines.isEmpty()
                ? plugin.getConfig().getString("hologram.text", "")
                : String.join("\n", lines);
    }
}
