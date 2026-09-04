package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern SECTION_HEX_PATTERN = Pattern.compile(
            "§x§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])"
    );
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9A-FK-ORa-fk-or])");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorUtil() {
    }

    public static Component deserialize(String message) {
        if (message == null) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        return MINI_MESSAGE.deserialize(convertLegacyCodes(message))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String translateHexColorCodes(String message) {
        return message == null ? null : LEGACY_SERIALIZER.serialize(deserialize(message));
    }

    private static String convertLegacyCodes(String message) {
        Matcher sectionHexMatcher = SECTION_HEX_PATTERN.matcher(message);
        StringBuffer sectionHexBuffer = new StringBuffer(message.length());
        while (sectionHexMatcher.find()) {
            String hex = sectionHexMatcher.group(1) + sectionHexMatcher.group(2)
                    + sectionHexMatcher.group(3) + sectionHexMatcher.group(4)
                    + sectionHexMatcher.group(5) + sectionHexMatcher.group(6);
            sectionHexMatcher.appendReplacement(sectionHexBuffer, Matcher.quoteReplacement("&#" + hex));
        }
        sectionHexMatcher.appendTail(sectionHexBuffer);

        String normalized = sectionHexBuffer.toString().replace('§', '&');
        Matcher hexMatcher = LEGACY_HEX_PATTERN.matcher(normalized);
        StringBuffer hexBuffer = new StringBuffer(normalized.length());
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement("<reset><!italic><#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(hexBuffer);

        Matcher legacyMatcher = LEGACY_PATTERN.matcher(hexBuffer.toString());
        StringBuffer result = new StringBuffer(hexBuffer.length());
        while (legacyMatcher.find()) {
            String tag = legacyTag(legacyMatcher.group(1).toLowerCase(Locale.ROOT).charAt(0));
            legacyMatcher.appendReplacement(result, Matcher.quoteReplacement(tag));
        }
        legacyMatcher.appendTail(result);
        return result.toString();
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "<reset><!italic><black>";
            case '1' -> "<reset><!italic><dark_blue>";
            case '2' -> "<reset><!italic><dark_green>";
            case '3' -> "<reset><!italic><dark_aqua>";
            case '4' -> "<reset><!italic><dark_red>";
            case '5' -> "<reset><!italic><dark_purple>";
            case '6' -> "<reset><!italic><gold>";
            case '7' -> "<reset><!italic><gray>";
            case '8' -> "<reset><!italic><dark_gray>";
            case '9' -> "<reset><!italic><blue>";
            case 'a' -> "<reset><!italic><green>";
            case 'b' -> "<reset><!italic><aqua>";
            case 'c' -> "<reset><!italic><red>";
            case 'd' -> "<reset><!italic><light_purple>";
            case 'e' -> "<reset><!italic><yellow>";
            case 'f' -> "<reset><!italic><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset><!italic>";
            default -> "";
        };
    }
}
