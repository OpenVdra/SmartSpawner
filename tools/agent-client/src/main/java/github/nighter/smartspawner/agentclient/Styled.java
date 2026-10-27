package github.nighter.smartspawner.agentclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Text as a check reads it: the plain string, and the runs of it that share one effective style.
 *
 * <p>A plugin's line is rarely one styled component. Legacy codes and MiniMessage both arrive as a root
 * with no style and a child per colour change, and an item's lore inherits vanilla's purple italic
 * unless the plugin turned it off. So the style that matters is the one each piece ends up drawn with,
 * which is what {@link FormattedText#visit} hands over: parents' styles already applied.
 *
 * <p>A segment carries {@code color} as {@code #RRGGBB} (null for the default) and only the decorations
 * that are on, so {@code "italic": true} on a lore segment is exactly the bug it looks like.
 */
final class Styled {
    private Styled() { }

    static JsonObject json(FormattedText text) {
        JsonArray segments = new JsonArray();
        StringBuilder plain = new StringBuilder();
        JsonObject[] last = {null};
        Style[] lastStyle = {null};
        text.visit((style, content) -> {
            if (content.isEmpty()) return Optional.empty();
            plain.append(content);
            // Neighbouring pieces with the same look are one segment, the way they read on screen.
            if (last[0] != null && same(style, lastStyle[0])) {
                last[0].addProperty("text", last[0].get("text").getAsString() + content);
                return Optional.empty();
            }
            JsonObject segment = new JsonObject();
            segment.addProperty("text", content);
            segment.addProperty("color", style.getColor() == null ? null
                    : String.format("#%06X", style.getColor().getValue() & 0xFFFFFF));
            if (style.isBold()) segment.addProperty("bold", true);
            if (style.isItalic()) segment.addProperty("italic", true);
            if (style.isUnderlined()) segment.addProperty("underlined", true);
            if (style.isStrikethrough()) segment.addProperty("strikethrough", true);
            if (style.isObfuscated()) segment.addProperty("obfuscated", true);
            segments.add(segment);
            last[0] = segment;
            lastStyle[0] = style;
            return Optional.empty();
        }, Style.EMPTY);
        JsonObject result = new JsonObject();
        result.addProperty("text", plain.toString());
        result.add("segments", segments);
        return result;
    }

    private static boolean same(Style a, Style b) {
        return java.util.Objects.equals(a.getColor(), b.getColor())
                && a.isBold() == b.isBold() && a.isItalic() == b.isItalic()
                && a.isUnderlined() == b.isUnderlined() && a.isStrikethrough() == b.isStrikethrough()
                && a.isObfuscated() == b.isObfuscated();
    }
}
