package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LanguageComponentFormatter {
    private LanguageComponentFormatter() {
    }

    public static Component translatableLootLine(String template, Material material, String amount, String chance) {
        return translatableLootLine(template, Component.translatable(material.translationKey()), amount, chance);
    }

    /**
     * Same as the material overload, but the item name is supplied as a ready component. This is how a
     * caller passes an item's effective name (for example a tipped arrow's potion-specific name) instead
     * of the plain material translation, so the loot line reads the way the item does in an inventory.
     */
    public static Component translatableLootLine(String template, Component itemName, String amount, String chance) {
        if (template == null) {
            return noItalic(Component.text(amount + " ")
                    .append(itemName)
                    .append(Component.text(" (" + chance + ")")));
        }

        String resolved = template
                .replace("{amount}", amount)
                .replace("{chance}", chance);

        String placeholder = "{item_name}";
        int index = resolved.indexOf(placeholder);
        if (index < 0) {
            return noItalic(ColorUtil.deserialize(resolved));
        }

        String beforeRaw = resolved.substring(0, index);
        String afterRaw = resolved.substring(index + placeholder.length());
        TextColor itemColor = extractLastColor(beforeRaw, TextColor.color(0xFFFFFF));

        Component before = ColorUtil.deserialize(beforeRaw);
        Component after = ColorUtil.deserialize(afterRaw);

        return noItalic(before
                .append(itemName.colorIfAbsent(itemColor))
                .append(after));
    }

    /**
     * Renders a lore template where a single placeholder is replaced by a ready component (an item's
     * effective name) rather than text. Every other line, and the text around the placeholder, keeps the
     * legacy colour handling. Used by GUIs that want a readable, correctly translated item name in the
     * lore instead of a raw config value such as an {@code nbt:} blob.
     */
    public static List<Component> loreWithItemName(
            List<String> template,
            Function<String, String> lineFormatter,
            String placeholder,
            Component itemName
    ) {
        LegacyComponentSerializer legacySerial = LegacyComponentSerializer.legacySection();
        List<Component> result = new ArrayList<>(template.size());

        for (String line : template) {
            int index = line.indexOf(placeholder);
            if (index < 0) {
                result.add(noItalic(legacySerial.deserialize(lineFormatter.apply(line))));
                continue;
            }

            String beforeRaw = line.substring(0, index);
            String afterRaw = line.substring(index + placeholder.length());
            TextColor itemColor = extractLastColor(beforeRaw, TextColor.color(0xFFFFFF));

            Component before = legacySerial.deserialize(lineFormatter.apply(beforeRaw));
            Component after = legacySerial.deserialize(lineFormatter.apply(afterRaw));
            result.add(noItalic(before.append(itemName.colorIfAbsent(itemColor)).append(after)));
        }

        return result;
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
                    result.add(noItalic(ColorUtil.deserialize(emptyRaw == null ? "" : emptyRaw)));
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
        return extractLastColor(ColorUtil.deserialize(text), defaultColor);
    }

    private static TextColor extractLastColor(Component component, TextColor inheritedColor) {
        TextColor color = component.color() != null ? component.color() : inheritedColor;
        for (Component child : component.children()) {
            color = extractLastColor(child, color);
        }
        return color;
    }
}
