package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a language string into text. Every string accepts MiniMessage ({@code <red>}, {@code <#5DB5F1>},
 * {@code <gradient:...>}), legacy codes ({@code &c}, {@code &#5DB5F1}, section signs), or both mixed.
 *
 * <p>Legacy codes are rewritten to MiniMessage tags first, keeping their meaning: a legacy colour ends
 * any bold or italic a legacy code started. Tags MiniMessage does not know, such as the
 * {@code <stack_size|range|delay>} in a usage line, stay literal text.</p>
 *
 * <p>Placeholder values never add formatting: plain values are inserted as text, so a player or item
 * name cannot inject {@code <red>}. A value that already carries legacy codes (an already formatted
 * line such as {@code {loot_items}}) keeps them, and only them.</p>
 */
public final class TextFormatter {
    private static final TagResolver SMALL_CAPS = TagResolver.resolver("smallcaps", (args, context) -> SmallCapsTag.INSTANCE);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(TagResolver.standard(), SMALL_CAPS))
            .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // &#RRGGBB, the spigot &x&R&R&G&G&B&B form, and single codes. '&' and the section sign alike.
    private static final Pattern LEGACY = Pattern.compile(
            "[&§]#([0-9a-fA-F]{6})"
                    + "|[&§][xX]((?:[&§][0-9a-fA-F]){6})"
                    + "|[&§]([0-9a-fA-Fk-oK-OrR])");
    private static final Pattern VALID_TAG_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    /** The small caps placeholder's original name. Tag names must be ASCII, so it also answers to this one. */
    private static final String SMALL_CAPS_ENTITY = "ᴇɴᴛɪᴛʏ";
    private static final String SMALL_CAPS_ENTITY_ALIAS = "entity_small";

    private TextFormatter() {}

    /** Parses {@code text} with its placeholders into a component. */
    public static Component parse(String text, Map<String, String> placeholders) {
        if (text == null || text.isEmpty()) return Component.empty();

        String template = legacyToMiniMessage(text);
        List<TagResolver> resolvers = new ArrayList<>();
        if (placeholders != null && !placeholders.isEmpty()) {
            int index = 0;
            for (Map.Entry<String, String> entry : withAliases(placeholders).entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                String token = "{" + entry.getKey() + "}";
                // Generated tag names work for any key, including uppercase and unicode ones.
                if (template.contains(token)) {
                    String tagName = "ss_placeholder_" + index++;
                    template = template.replace(token, "<" + tagName + ">");
                    resolvers.add(placeholder(tagName, value));
                }
                String lowerKey = entry.getKey().toLowerCase(Locale.ROOT);
                // Also as <key>, so owners can write placeholders the MiniMessage way.
                if (VALID_TAG_NAME.matcher(lowerKey).matches() && template.contains("<" + lowerKey)
                        && !MINI_MESSAGE.tags().has(lowerKey)) {
                    resolvers.add(placeholder(lowerKey, value));
                }
            }
        }
        return MINI_MESSAGE.deserialize(template, TagResolver.resolver(resolvers));
    }

    /** Parses {@code text} and returns it as a section-sign string for Bukkit's String APIs. */
    public static String toLegacy(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        if (text.isEmpty()) return text;
        return SECTION.serialize(parse(text, placeholders));
    }

    public static String toLegacy(String text) {
        return toLegacy(text, Collections.emptyMap());
    }

    /** Parses {@code text} and drops all formatting, for the console. */
    public static String toPlain(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(parse(text, placeholders));
    }

    /** A section-sign string, as {@link #toLegacy} returns, back into a component. */
    public static Component fromLegacy(String legacy) {
        return legacy == null ? Component.empty() : SECTION.deserialize(legacy);
    }

    /**
     * Rewrites legacy codes to MiniMessage tags. A colour after a legacy decoration is preceded by
     * {@code <reset>}, because in legacy text a colour code clears bold, italic and the rest.
     */
    public static String legacyToMiniMessage(String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) return text;

        Matcher matcher = LEGACY.matcher(text);
        StringBuilder result = new StringBuilder(text.length() + 16);
        boolean decorated = false;
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            last = matcher.end();

            String color = null;
            if (matcher.group(1) != null) {
                color = "#" + matcher.group(1);
            } else if (matcher.group(2) != null) {
                color = "#" + matcher.group(2).replaceAll("[&§]", "");
            } else {
                char code = Character.toLowerCase(matcher.group(3).charAt(0));
                int colorIndex = Character.digit(code, 16);
                if (colorIndex >= 0) {
                    color = COLOR_NAMES[colorIndex];
                } else if (code == 'r') {
                    result.append("<reset>");
                    decorated = false;
                    continue;
                } else {
                    result.append(switch (code) {
                        case 'l' -> "<bold>";
                        case 'o' -> "<italic>";
                        case 'n' -> "<underlined>";
                        case 'm' -> "<strikethrough>";
                        default -> "<obfuscated>";
                    });
                    decorated = true;
                    continue;
                }
            }

            if (decorated) {
                result.append("<reset>");
                decorated = false;
            }
            result.append('<').append(color.toLowerCase(Locale.ROOT)).append('>');
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    private static TagResolver placeholder(String name, String value) {
        if (value.indexOf('§') >= 0 || value.indexOf('&') >= 0) {
            // Already formatted: keep its legacy codes, but escape tags so the value cannot add new ones.
            String escaped = MINI_MESSAGE.escapeTags(value);
            return Placeholder.component(name, MINI_MESSAGE.deserialize(legacyToMiniMessage(escaped)));
        }
        return Placeholder.unparsed(name, value);
    }

    private static Map<String, String> withAliases(Map<String, String> placeholders) {
        boolean hasOriginal = placeholders.containsKey(SMALL_CAPS_ENTITY);
        boolean hasAlias = placeholders.containsKey(SMALL_CAPS_ENTITY_ALIAS);
        if (hasOriginal == hasAlias) return placeholders;

        Map<String, String> aliased = new java.util.HashMap<>(placeholders);
        if (hasOriginal) aliased.put(SMALL_CAPS_ENTITY_ALIAS, placeholders.get(SMALL_CAPS_ENTITY));
        else aliased.put(SMALL_CAPS_ENTITY, placeholders.get(SMALL_CAPS_ENTITY_ALIAS));
        return aliased;
    }

    /** {@code <smallcaps>text</smallcaps>}: letters become small capitals; scripts without them are unchanged. */
    private static final class SmallCapsTag implements Modifying {
        private static final SmallCapsTag INSTANCE = new SmallCapsTag();

        @Override
        public Component apply(@NotNull Component current, int depth) {
            if (current instanceof TextComponent text) {
                return text.content(SmallCapsFormatter.toSmallCaps(text.content())).children(Collections.emptyList());
            }
            return current.children(Collections.emptyList());
        }
    }
}
