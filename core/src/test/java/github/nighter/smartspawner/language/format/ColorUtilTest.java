package github.nighter.smartspawner.language.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ColorUtilTest {
    @Test
    void supportsMiniMessageColorsAndDisablesItalicByDefault() {
        Component component = ColorUtil.deserialize("<#E500D5>Spawner <dark_gray>level");

        assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
        assertEquals(TextColor.color(0xE500D5), component.color());
        assertFalse(ColorUtil.translateHexColorCodes("<#E500D5>Spawner").contains("<#E500D5>"));
    }

    @Test
    void keepsLegacyHexSupport() {
        Component component = ColorUtil.deserialize("&#E500D5Spawner");

        assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
        assertEquals(TextColor.color(0xE500D5), component.color());
    }

    @Test
    void acceptsAlreadySerializedLegacyHexFromPlaceholders() {
        Component component = ColorUtil.deserialize("§x§f§f§d§c§4§8• Gunpowder");

        assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
        assertEquals(TextColor.color(0xFFDC48), component.color());
    }
}
