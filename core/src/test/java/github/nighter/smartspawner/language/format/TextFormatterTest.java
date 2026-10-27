package github.nighter.smartspawner.language.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Language strings accept MiniMessage, legacy codes, or both. */
class TextFormatterTest {

    private static String legacy(String text) {
        return TextFormatter.toLegacy(text);
    }

    private static String plain(String text, Map<String, String> placeholders) {
        return TextFormatter.toPlain(text, placeholders);
    }

    @Test
    @DisplayName("legacy codes only")
    void legacyOnly() {
        assertEquals("§aHello §cworld", legacy("&aHello &cworld"));
        assertEquals("§aHello", legacy("§aHello"), "section signs are read like '&'");
    }

    @Test
    @DisplayName("MiniMessage only")
    void miniMessageOnly() {
        assertEquals("§cHello", legacy("<red>Hello"));
        assertEquals("§x§5§d§b§5§f§1Hi", legacy("<#5DB5F1>Hi"));
    }

    @Test
    @DisplayName("legacy and MiniMessage mixed in one string")
    void mixed() {
        assertEquals("§cA §bB", legacy("<red>A &bB"));
        assertEquals("§x§5§d§b§5§f§1A §lB", legacy("&#5DB5F1A <bold>B"));
    }

    @Test
    @DisplayName("hex in the &x&R&R&G&G&B&B form")
    void spigotHex() {
        assertEquals("§x§5§d§b§5§f§1Hi", legacy("&x&5&D&B&5&F&1Hi"));
    }

    @Test
    @DisplayName("literal angle brackets in a usage line stay as text")
    void usageLineBracketsStayLiteral() {
        String usage = "&#3498db/ss set <stack_size|range|delay> <value>";
        assertEquals("/ss set <stack_size|range|delay> <value>", plain(usage, Map.of()));
    }

    @Test
    @DisplayName("a legacy colour after &l clears the bold, as in legacy text")
    void legacyColourClearsBold() {
        assertEquals("§aText", legacy("&l&aText"));
        assertEquals("§a§lText", legacy("&a&lText"));
    }

    @Test
    @DisplayName("{ᴇɴᴛɪᴛʏ} and {entity_small} are the same placeholder")
    void smallCapsEntityAlias() {
        assertEquals("ᴢᴏᴍʙɪᴇ spawner", plain("{entity_small} spawner", Map.of("ᴇɴᴛɪᴛʏ", "ᴢᴏᴍʙɪᴇ")));
        assertEquals("ᴢᴏᴍʙɪᴇ spawner", plain("{ᴇɴᴛɪᴛʏ} spawner", Map.of("entity_small", "ᴢᴏᴍʙɪᴇ")));
    }

    @Test
    @DisplayName("a <red> inside a placeholder value stays literal")
    void placeholderValueCannotInjectTags() {
        String result = legacy("<green>Hi {player}", Map.of("player", "<red>evil"));
        assertEquals("§aHi <red>evil", result);
    }

    @Test
    @DisplayName("an already formatted value keeps its legacy colours")
    void formattedValueKeepsColours() {
        assertEquals("Loot: §eStone", legacy("Loot: {loot_items}", Map.of("loot_items", "§eStone")));
    }

    @Test
    @DisplayName("placeholders also work the MiniMessage way, and with any key")
    void miniMessagePlaceholders() {
        assertEquals("5 items", plain("<amount> items", Map.of("amount", "5")));
        assertEquals("Nether", plain("{NETHER}", Map.of("NETHER", "Nether")));
        assertEquals("{unknown} stays", plain("{unknown} stays", Map.of("amount", "5")));
    }

    @Test
    @DisplayName("<smallcaps> turns letters into small capitals")
    void smallCapsTag() {
        assertEquals("ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ", plain("<smallcaps>Open Storage</smallcaps>", Map.of()));
        assertEquals("ᴢᴏᴍʙɪᴇ", plain("<smallcaps>{entity}</smallcaps>", Map.of("entity", "Zombie")));
        assertEquals("刷怪笼", plain("<smallcaps>刷怪笼</smallcaps>", Map.of()), "no-op without small capitals");
    }

    @Test
    @DisplayName("a gradient survives as per-letter hex colours")
    void gradient() {
        String result = legacy("<gradient:#5DB5F1:#FFFFFF>Spawner</gradient>");
        assertTrue(result.startsWith("§x§5§d§b§5§f§1S"), result);
        assertFalse(result.contains("<"), result);
    }

    private static String legacy(String text, Map<String, String> placeholders) {
        return TextFormatter.toLegacy(text, placeholders);
    }
}
