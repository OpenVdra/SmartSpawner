package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageComponentFormatter {
    private static final Pattern HEX_TEMPLATE_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<Character, TextColor> LEGACY_COLOR_MAP;

    static {
        Map<Character, TextColor> colors = new HashMap<>(16);
        colors.put('0', TextColor.color(0x000000));
        colors.put('1', TextColor.color(0x0000AA));
        colors.put('2', TextColor.color(0x00AA00));
        colors.put('3', TextColor.color(0x00AAAA));
        colors.put('4', TextColor.color(0xAA0000));
        colors.put('5', TextColor.color(0xAA00AA));
        colors.put('6', TextColor.color(0xFFAA00));
        colors.put('7', TextColor.color(0xAAAAAA));
        colors.put('8', TextColor.color(0x555555));
        colors.put('9', TextColor.color(0x5555FF));
        colors.put('a', TextColor.color(0x55FF55));
        colors.put('b', TextColor.color(0x55FFFF));
        colors.put('c', TextColor.color(0xFF5555));
        colors.put('d', TextColor.color(0xFF55FF));
        colors.put('e', TextColor.color(0xFFFF55));
        colors.put('f', TextColor.color(0xFFFFFF));
        LEGACY_COLOR_MAP = Collections.unmodifiableMap(colors);
    }

    private LanguageComponentFormatter() {
    }

    public static Component translatableLootLine(String template, Material material, String amount, String chance) {
        if (template == null) {
            return noItalic(Component.text(amount + " ")
                    .append(Component.translatable(material.translationKey()))
                    .append(Component.text(" (" + chance + ")")));
        }

        String resolved = template
                .replace("{amount}", amount)
                .replace("{chance}", chance);

        String placeholder = "{item_name}";
        int index = resolved.indexOf(placeholder);
        if (index < 0) {
            return noItalic(LegacyComponentSerializer.legacySection()
                    .deserialize(ColorUtil.translateHexColorCodes(resolved)));
        }

        String beforeRaw = resolved.substring(0, index);
        String afterRaw = resolved.substring(index + placeholder.length());
        TextColor itemColor = extractLastColor(beforeRaw, TextColor.color(0xFFFFFF));

        Component before = LegacyComponentSerializer.legacySection()
                .deserialize(ColorUtil.translateHexColorCodes(beforeRaw));
        Component after = LegacyComponentSerializer.legacySection()
                .deserialize(ColorUtil.translateHexColorCodes(afterRaw));

        return noItalic(before
                .append(Component.translatable(material.translationKey()).color(itemColor))
                .append(after));
    }

    public static List<Component> loreComponents(
            List<String> template,
            Function<String, String> lineFormatter,
            Supplier<String> emptyLootLine,
            List<Component> lootItemComponents
    ) {
        LegacyComponentSerializer legacySerial = LegacyComponentSerializer.legacySection();
        List<Component> result = new ArrayList<>(template.size() + lootItemComponents.size());

        for (String line : template) {
            if (line.contains("{loot_items}")) {
                if (lootItemComponents.isEmpty()) {
                    String emptyRaw = emptyLootLine.get();
                    result.add(noItalic(legacySerial.deserialize(ColorUtil.translateHexColorCodes(emptyRaw == null ? "" : emptyRaw))));
                } else {
                    result.addAll(lootItemComponents);
                }
                continue;
            }

            result.add(noItalic(legacySerial.deserialize(lineFormatter.apply(line))));
        }

        return result;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static TextColor extractLastColor(String text, TextColor defaultColor) {
        if (text == null || text.isEmpty()) return defaultColor;

        TextColor last = null;
        int lastPosition = -1;
        Matcher matcher = HEX_TEMPLATE_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > lastPosition) {
                lastPosition = matcher.start();
                last = TextColor.color(Integer.parseInt(matcher.group(1), 16));
            }
        }

        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '&') continue;
            TextColor legacyColor = LEGACY_COLOR_MAP.get(Character.toLowerCase(text.charAt(i + 1)));
            if (legacyColor != null && i > lastPosition) {
                lastPosition = i;
                last = legacyColor;
            }
        }

        return last != null ? last : defaultColor;
    }
}
