package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing a player clicks in SmartSpawner text may run a command it was not written to run: placeholder
 * values never become tags, item text never carries click events, and data tags never resolve.
 */
class TextFormatterSafetyTest {

    private static final String RUN_OP = "<click:run_command:/op attacker>free stuff</click>";

    @Test
    @DisplayName("display text has no click, hover or insert: an item name could carry them into chat")
    void displayTextIsNeverInteractive() {
        Component text = TextFormatter.parse(RUN_OP + "<hover:show_text:'x'>h</hover><insert:/op>i</insert>", Map.of());

        assertTrue(clicks(text).isEmpty());
        assertFalse(hasHover(text));
        assertFalse(hasInsertion(text));
        assertTrue(TextFormatter.toPlain(text).contains("<click:run_command:/op attacker>"),
                "the tag stays visible so the owner sees it is not available there");
    }

    @Test
    @DisplayName("a chat message from the language file may use click and hover")
    void chatTemplateMayBeInteractive() {
        Component text = TextFormatter.parseChat("<click:run_command:/ss list><hover:show_text:'List'>Open", Map.of());

        assertEquals(List.of(ClickEvent.runCommand("/ss list")), clicks(text));
        assertTrue(hasHover(text));
    }

    @Test
    @DisplayName("data tags never resolve, not even in chat")
    void dataTagsAreBlocked() {
        Component text = TextFormatter.parseChat("<selector:@a> <score:attacker:kills> <nbt:block:'0 0 0':Items>", Map.of());

        assertFalse(contains(text, SelectorComponent.class));
        assertFalse(contains(text, ScoreComponent.class));
        assertFalse(contains(text, NBTComponent.class));
        assertTrue(TextFormatter.toPlain(text).contains("<selector:@a>"));
    }

    @Test
    @DisplayName("a click tag inside a player's name or item name stays text, in chat too")
    void placeholderValueCannotAddClick() {
        for (String template : List.of("Sold by {player}", "Sold by <player>")) {
            Component text = TextFormatter.parseChat(template, Map.of("player", RUN_OP));

            assertTrue(clicks(text).isEmpty(), template);
            assertEquals("Sold by " + RUN_OP, TextFormatter.toPlain(text), template);
        }
    }

    @Test
    @DisplayName("a value with legacy colours keeps them but still cannot add a click")
    void legacyValueCannotAddClick() {
        Component text = TextFormatter.parseChat("Item: {item}", Map.of("item", "&a" + RUN_OP + "&r\\<red>"));

        assertTrue(clicks(text).isEmpty());
        assertEquals("Item: " + RUN_OP + "\\<red>", TextFormatter.toPlain(text));
    }

    @Test
    @DisplayName("escaping and closing tags in a value do not break out of it")
    void escapedValueStaysText() {
        for (String value : List.of("\\" + RUN_OP, "</ss_placeholder_0>" + RUN_OP, "<reset>" + RUN_OP, "&r</click>" + RUN_OP)) {
            Component text = TextFormatter.parseChat("<green>{v}", Map.of("v", value));

            assertTrue(clicks(text).isEmpty(), value);
        }
    }

    @Test
    @DisplayName("a value is never pasted into the command of a click the owner wrote")
    void valueNeverReachesClickCommand() {
        Component text = TextFormatter.parseChat("<click:run_command:/give {player} diamond>Claim</click>",
                Map.of("player", "@a"));

        for (ClickEvent click : clicks(text)) {
            assertFalse(click.value().contains("@a"), click.value());
        }
    }

    @Test
    @DisplayName("an item's own name loses its click and hover before it is placed in our text")
    void componentValueIsMadeInert() {
        Component itemName = Component.text("Sword")
                .clickEvent(ClickEvent.runCommand("/op attacker"))
                .hoverEvent(HoverEvent.showText(Component.text("x")))
                .append(Component.text(" of doom").clickEvent(ClickEvent.runCommand("/op attacker")));

        Component text = TextFormatter.parseChat("<yellow>{item_name}", Map.of(), Map.of("item_name", itemName));

        assertTrue(clicks(text).isEmpty());
        assertFalse(hasHover(text));
        assertEquals("Sword of doom", TextFormatter.toPlain(text));
    }

    @Test
    @DisplayName("legacy codes in a value become styles without parsing anything else")
    void legacyValueStyles() {
        Component value = TextFormatter.legacyValue("&cRed &lBold&aGreen");
        Component bold = value.children().get(1);

        assertEquals(NamedTextColor.RED, value.children().get(0).color());
        assertEquals(TextDecoration.State.TRUE, bold.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GREEN, value.children().get(2).color());
        assertEquals(TextDecoration.State.NOT_SET, value.children().get(2).decoration(TextDecoration.BOLD),
                "a legacy colour ends the bold, as in legacy text");
    }

    private static List<ClickEvent> clicks(Component component) {
        List<ClickEvent> result = new ArrayList<>();
        walk(component, c -> {
            if (c.clickEvent() != null) result.add(c.clickEvent());
        });
        return result;
    }

    private static boolean hasHover(Component component) {
        boolean[] found = {false};
        walk(component, c -> found[0] |= c.hoverEvent() != null);
        return found[0];
    }

    private static boolean hasInsertion(Component component) {
        boolean[] found = {false};
        walk(component, c -> found[0] |= c.insertion() != null);
        return found[0];
    }

    private static boolean contains(Component component, Class<?> type) {
        boolean[] found = {false};
        walk(component, c -> found[0] |= type.isInstance(c));
        return found[0];
    }

    private static void walk(Component component, java.util.function.Consumer<Component> visitor) {
        visitor.accept(component);
        component.children().forEach(child -> walk(child, visitor));
        if (component.hoverEvent() != null && component.hoverEvent().value() instanceof Component hover) {
            walk(hover, visitor);
        }
    }
}
