package github.nighter.smartspawner.language.format;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String translateHexColorCodes(String message) {
        if (message == null) return null;

        // Convert hex color codes
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hexColor).toString();
            matcher.appendReplacement(buffer, replacement);
        }

        matcher.appendTail(buffer);

        // Convert standard color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
