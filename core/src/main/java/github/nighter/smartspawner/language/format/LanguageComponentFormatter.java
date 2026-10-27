package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Lore building shared by the item and GUI sections. */
public final class LanguageComponentFormatter {

    private LanguageComponentFormatter() {
    }

    public static Component translatableLootLine(String template, Material material, String amount, String chance) {
        return translatableLootLine(template, Component.translatable(material.translationKey()), amount, chance);
    }

    /**
     * One loot line. {@code {item_name}} is the item's own name as a component, so a vanilla name shows
     * in each client's language and a tipped arrow reads "Arrow of Strength". It takes the colour in
     * front of it unless it has its own.
     */
    public static Component translatableLootLine(String template, Component itemName, String amount, String chance) {
        if (template == null) {
            return TextFormatter.noItalic(Component.text(amount + " ")
                    .append(TextFormatter.inert(itemName))
                    .append(Component.text(" (" + chance + ")")));
        }
        return TextFormatter.noItalic(TextFormatter.parse(template,
                Map.of("amount", amount, "chance", chance),
                Map.of("item_name", itemName)));
    }

    /**
     * Lore lines with expansions. A line that holds an expansion's placeholder becomes one line per
     * component in its list, none when the list is empty. When the placeholder is the whole line the
     * component is used as it is; otherwise each one is placed into the line's text.
     */
    public static List<Component> lore(List<String> template, Map<String, String> placeholders,
                                       Map<String, List<Component>> expansions,
                                       Function<String, Component> lineFormatter) {
        List<Component> result = new ArrayList<>(template.size());
        for (String line : template) {
            String expansion = findExpansion(line, expansions);
            if (expansion == null) {
                result.add(TextFormatter.noItalic(lineFormatter.apply(line)));
                continue;
            }

            List<Component> items = expansions.get(expansion);
            boolean wholeLine = line.trim().equals("{" + expansion + "}");
            for (Component item : items) {
                result.add(TextFormatter.noItalic(wholeLine
                        ? item
                        : TextFormatter.parse(line, placeholders, Map.of(expansion, item))));
            }
        }
        return result;
    }

    private static String findExpansion(String line, Map<String, List<Component>> expansions) {
        if (expansions.isEmpty() || line.indexOf('{') < 0) return null;
        for (String key : expansions.keySet()) {
            if (line.contains("{" + key + "}")) return key;
        }
        return null;
    }
}
