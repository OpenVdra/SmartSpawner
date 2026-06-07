package github.nighter.smartspawner.language.format;

import org.bukkit.configuration.file.YamlConfiguration;

public final class NumberFormatter {
    private final YamlConfiguration formattingConfig;

    public NumberFormatter(YamlConfiguration formattingConfig) {
        this.formattingConfig = formattingConfig;
    }

    public String format(double number) {
        String format;
        double value;

        if (number >= 1_000_000_000_000L) {
            format = formattingConfig.getString("format_number.trillion", "{s}T");
            value = Math.round(number / 1_000_000_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000_000_000L) {
            format = formattingConfig.getString("format_number.billion", "{s}B");
            value = Math.round(number / 1_000_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000_000L) {
            format = formattingConfig.getString("format_number.million", "{s}M");
            value = Math.round(number / 1_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000L) {
            format = formattingConfig.getString("format_number.thousand", "{s}K");
            value = Math.round(number / 1_000.0 * 10) / 10.0;
        } else {
            format = formattingConfig.getString("format_number.default", "{s}");
            value = Math.round(number * 10) / 10.0;
        }

        return format.replace("{s}", formatDecimal(value));
    }

    private String formatDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        } else {
            return String.valueOf(value);
        }
    }

    public String formatDefault(double number) {
        if (number >= 1_000_000_000_000L) {
            double value = Math.round(number / 1_000_000_000_000.0 * 10) / 10.0;
            return formatDecimal(value) + "T";
        } else if (number >= 1_000_000_000L) {
            double value = Math.round(number / 1_000_000_000.0 * 10) / 10.0;
            return formatDecimal(value) + "B";
        } else if (number >= 1_000_000L) {
            double value = Math.round(number / 1_000_000.0 * 10) / 10.0;
            return formatDecimal(value) + "M";
        } else if (number >= 1_000L) {
            double value = Math.round(number / 1_000.0 * 10) / 10.0;
            return formatDecimal(value) + "K";
        } else {
            double value = Math.round(number * 10) / 10.0;
            return formatDecimal(value);
        }
    }
}
