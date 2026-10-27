package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * <p>What a string may do depends on where it is shown, so nothing a player clicks can run a command
 * it was not meant to:</p>
 * <ul>
 *   <li>{@link #parse} is for everything that is not chat: item names and lore, inventory titles,
 *       holograms, titles, action bars and boss bars. {@code <click>}, {@code <hover>} and
 *       {@code <insert>} are not available there. On an item they would travel with it, and any
 *       plugin that later shows the item's name in chat would make them live.</li>
 *   <li>{@link #parseChat} is for chat messages and adds {@code <click>}, {@code <hover>} and
 *       {@code <insert>}, written by the server owner in the language file.</li>
 *   <li>Neither allows {@code <nbt>}, {@code <selector>} or {@code <score>}, which read server data
 *       when something resolves them.</li>
 * </ul>
 *
 * <p>Placeholder values are never parsed. A player, world or item name is inserted as text, so
 * {@code <click:run_command:...>} in an anvil-renamed item stays visible text. A value that carries
 * legacy codes (an already formatted line such as {@code {loot_items}}) keeps its colours and
 * decorations and nothing else. A component value has its click, hover and insertion removed.</p>
 */
public final class TextFormatter {
    /** Tags that read data from the server. Never resolved. */
    private static final Set<String> DATA_TAGS = Set.of("nbt", "data", "selector", "sel", "score");
    /** Tags that make text do something when clicked, hovered or shift-clicked. Chat only. */
    private static final Set<String> INTERACTIVE_TAGS = Set.of("click", "hover", "insert", "insertion");

    private static final TagResolver SMALL_CAPS = TagResolver.resolver("smallcaps", (args, context) -> SmallCapsTag.INSTANCE);
    private static final MiniMessage DISPLAY = MiniMessage.builder()
            .tags(TagResolver.resolver(new FilteredResolver(TagResolver.standard(), true), SMALL_CAPS))
            .build();
    private static final MiniMessage CHAT = MiniMessage.builder()
            .tags(TagResolver.resolver(new FilteredResolver(TagResolver.standard(), false), SMALL_CAPS))
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
    private static final NamedTextColor[] COLORS = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
    };

    /** The small caps placeholder's original name. Tag names must be ASCII, so it also answers to this one. */
    private static final String SMALL_CAPS_ENTITY = "ᴇɴᴛɪᴛʏ";
    private static final String SMALL_CAPS_ENTITY_ALIAS = "entity_small";

    private TextFormatter() {}

    /** Parses {@code text} with its placeholders, for anything that is not a chat message. */
    public static Component parse(String text, Map<String, String> placeholders) {
        return parse(DISPLAY, text, placeholders, Collections.emptyMap());
    }

    /** As {@link #parse(String, Map)}, with placeholders whose value is a ready component (an item's name). */
    public static Component parse(String text, Map<String, String> placeholders,
                                  Map<String, ? extends ComponentLike> components) {
        return parse(DISPLAY, text, placeholders, components);
    }

    /** Parses a chat message: like {@link #parse(String, Map)}, plus {@code <click>}, {@code <hover>} and {@code <insert>}. */
    public static Component parseChat(String text, Map<String, String> placeholders) {
        return parse(CHAT, text, placeholders, Collections.emptyMap());
    }

    public static Component parseChat(String text, Map<String, String> placeholders,
                                      Map<String, ? extends ComponentLike> components) {
        return parse(CHAT, text, placeholders, components);
    }

    private static Component parse(MiniMessage miniMessage, String text, Map<String, String> placeholders,
                                   Map<String, ? extends ComponentLike> components) {
        if (text == null || text.isEmpty()) return Component.empty();

        String template = legacyToMiniMessage(text);
        List<TagResolver> resolvers = new ArrayList<>();
        int[] index = {0};
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : withAliases(placeholders).entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                template = bind(miniMessage, template, entry.getKey(), index, resolvers,
                        name -> placeholder(name, value));
            }
        }
        if (components != null && !components.isEmpty()) {
            for (Map.Entry<String, ? extends ComponentLike> entry : components.entrySet()) {
                Component value = entry.getValue() == null ? Component.empty() : inert(entry.getValue().asComponent());
                template = bind(miniMessage, template, entry.getKey(), index, resolvers,
                        name -> Placeholder.component(name, value));
            }
        }
        try {
            return miniMessage.deserialize(template, TagResolver.resolver(resolvers));
        } catch (ParsingException e) {
            // Only strict mode throws, but a broken string must never take a GUI down with it.
            return Component.text(text);
        }
    }

    /**
     * Points {@code {key}} (and {@code <key>}) in the template at a resolver. Generated tag names work
     * for any key, including uppercase and unicode ones.
     */
    private static String bind(MiniMessage miniMessage, String template, String key, int[] index,
                               List<TagResolver> resolvers, java.util.function.Function<String, TagResolver> factory) {
        String token = "{" + key + "}";
        if (template.contains(token)) {
            String tagName = "ss_placeholder_" + index[0]++;
            template = template.replace(token, "<" + tagName + ">");
            resolvers.add(factory.apply(tagName));
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        // Also as <key>, so owners can write placeholders the MiniMessage way.
        if (VALID_TAG_NAME.matcher(lowerKey).matches() && template.contains("<" + lowerKey)
                && !miniMessage.tags().has(lowerKey)) {
            resolvers.add(factory.apply(lowerKey));
        }
        return template;
    }

    /** Parses {@code text} and returns it as a section-sign string, for the few APIs that still take one. */
    public static String toLegacy(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        if (text.isEmpty()) return text;
        return SECTION.serialize(parse(text, placeholders));
    }

    public static String toLegacy(String text) {
        return toLegacy(text, Collections.emptyMap());
    }

    /** A component as a section-sign string. */
    public static String toLegacy(Component component) {
        return component == null ? "" : SECTION.serialize(component);
    }

    /** Parses {@code text} and drops all formatting, for the console. */
    public static String toPlain(String text, Map<String, String> placeholders) {
        if (text == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(parse(text, placeholders));
    }

    public static String toPlain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** A section-sign string back into a component. Legacy strings carry no click events, so this is safe for any input. */
    public static Component fromLegacy(String legacy) {
        return legacy == null ? Component.empty() : SECTION.deserialize(legacy);
    }

    /**
     * {@code component} without click, hover or insertion, at any depth. Used on components that come
     * from outside the language files, such as an item's name, before they are placed in our text.
     */
    public static Component inert(Component component) {
        if (component == null) return Component.empty();
        Component result = component;
        Style style = result.style();
        if (style.clickEvent() != null || style.hoverEvent() != null || style.insertion() != null) {
            result = result.style(style.toBuilder().clickEvent(null).hoverEvent(null).insertion(null).build());
        }
        List<Component> children = result.children();
        if (!children.isEmpty()) {
            List<Component> cleaned = new ArrayList<>(children.size());
            boolean changed = false;
            for (Component child : children) {
                Component inertChild = inert(child);
                changed |= inertChild != child;
                cleaned.add(inertChild);
            }
            if (changed) result = result.children(cleaned);
        }
        return result;
    }

    /**
     * Item text: italic off unless the text asks for it, because the client shows custom item names
     * and lore in italic by default.
     */
    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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

    /**
     * A placeholder value with legacy codes as a component, without parsing anything else in it: the
     * text between the codes is inserted as it is, so the value cannot add tags.
     */
    public static Component legacyValue(String value) {
        if (value == null || value.isEmpty()) return Component.empty();
        Matcher matcher = LEGACY.matcher(value);
        List<Component> parts = new ArrayList<>();
        Style style = Style.empty();
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                parts.add(Component.text(value.substring(last, matcher.start()), style));
            }
            last = matcher.end();
            if (matcher.group(1) != null) {
                style = Style.style(TextColor.color(Integer.parseInt(matcher.group(1), 16)));
            } else if (matcher.group(2) != null) {
                style = Style.style(TextColor.color(Integer.parseInt(matcher.group(2).replaceAll("[&§]", ""), 16)));
            } else {
                char code = Character.toLowerCase(matcher.group(3).charAt(0));
                int colorIndex = Character.digit(code, 16);
                if (colorIndex >= 0) {
                    style = Style.style(COLORS[colorIndex]);
                } else if (code == 'r') {
                    style = Style.empty();
                } else {
                    style = style.decoration(switch (code) {
                        case 'l' -> TextDecoration.BOLD;
                        case 'o' -> TextDecoration.ITALIC;
                        case 'n' -> TextDecoration.UNDERLINED;
                        case 'm' -> TextDecoration.STRIKETHROUGH;
                        default -> TextDecoration.OBFUSCATED;
                    }, true);
                }
            }
        }
        if (last < value.length()) {
            parts.add(Component.text(value.substring(last), style));
        }
        return parts.size() == 1 ? parts.getFirst() : Component.empty().children(parts);
    }

    private static TagResolver placeholder(String name, String value) {
        if (value.indexOf('§') >= 0 || value.indexOf('&') >= 0) {
            return Placeholder.component(name, legacyValue(value));
        }
        return Placeholder.unparsed(name, value);
    }

    private static Map<String, String> withAliases(Map<String, String> placeholders) {
        boolean hasOriginal = placeholders.containsKey(SMALL_CAPS_ENTITY);
        boolean hasAlias = placeholders.containsKey(SMALL_CAPS_ENTITY_ALIAS);
        if (hasOriginal == hasAlias) return placeholders;

        Map<String, String> aliased = new HashMap<>(placeholders);
        if (hasOriginal) aliased.put(SMALL_CAPS_ENTITY_ALIAS, placeholders.get(SMALL_CAPS_ENTITY));
        else aliased.put(SMALL_CAPS_ENTITY, placeholders.get(SMALL_CAPS_ENTITY_ALIAS));
        return aliased;
    }

    /**
     * The standard tags minus the blocked ones. A blocked tag is treated as unknown, so it stays in the
     * text as written and shows the owner it is not available there.
     */
    private record FilteredResolver(TagResolver delegate, boolean blockInteractive) implements TagResolver {
        private boolean blocked(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return DATA_TAGS.contains(lower) || (blockInteractive && INTERACTIVE_TAGS.contains(lower));
        }

        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx)
                throws ParsingException {
            return blocked(name) ? null : delegate.resolve(name, arguments, ctx);
        }

        @Override
        public boolean has(@NotNull String name) {
            return !blocked(name) && delegate.has(name);
        }
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
