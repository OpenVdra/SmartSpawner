package github.nighter.smartspawner.updates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.resource;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The retirement of the {@code en_US_DonutSMP} locales in favour of {@code gui_layouts/<layout>/texts.yml}.
 */
class LayoutTextsMigrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @TempDir
    Path folder;

    private static final String BASE_GUI = """
            sell_button:
              name: '&aSell all'
              lore:
                - '&7Sell everything'
            return_button:
              name: '&cBack'
            """;

    private static final String DONUT_TEXTS = """
            # SmartSpawner DonutSMP layout texts
            sell_button:
              name: '<#00F986>SELL ALL'
            """;

    private static final String BASE_MESSAGES = """
            prefix: '&8[SS] '
            no_exp:
              message: 'No exp'
              sound: entity.villager.no
            """;

    private static final Function<String, InputStream> RESOURCES = path -> {
        String yaml = Map.of(
                "language/en_US/gui.yml", BASE_GUI,
                "language/en_US/messages.yml", BASE_MESSAGES,
                "gui_layouts/DonutSMP/texts.yml", DONUT_TEXTS
        ).get(path);
        return yaml == null ? null : resource(yaml);
    };

    private void file(String path, String content) {
        try {
            Files.createDirectories(folder.resolve(path).getParent());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        write(folder, path, content);
    }

    /** The stock old DonutSMP folder: en_US's files, except gui.yml carries the theme. */
    private void stockDonutFolder() {
        file("language/en_US_DonutSMP/gui.yml", """
                sell_button:
                  name: '&#00F986SELL ALL'
                  lore:
                    - '&7Sell everything'
                return_button:
                  name: '&cBack'
                """);
        file("language/en_US_DonutSMP/messages.yml", BASE_MESSAGES);
    }

    private boolean migrate() {
        return LayoutTextsMigration.migrate(folder.toFile(), RESOURCES, TODAY, silentLogger());
    }

    @Test
    @DisplayName("the active DonutSMP locale becomes en_US with the DonutSMP layout")
    void activeLocaleSwitchesToLayout() {
        write(folder, "config.yml", "language: en_US_DonutSMP\ngui_layout: default\n");
        stockDonutFolder();

        assertTrue(migrate(), "config.yml was rewritten");

        YamlConfiguration config = read(folder, "config.yml");
        assertEquals("en_US", config.getString("language"));
        assertEquals("DonutSMP", config.getString("gui_layout"));
        assertFalse(Files.exists(folder.resolve("language/en_US_DonutSMP")));
    }

    @Test
    @DisplayName("a gui_layout the owner already chose is kept")
    void chosenLayoutIsKept() {
        write(folder, "config.yml", "language: en_US_DonutSMP\ngui_layout: DonutSMP_v2\n");
        stockDonutFolder();

        migrate();

        assertEquals("DonutSMP_v2", read(folder, "config.yml").getString("gui_layout"));
    }

    @Test
    @DisplayName("a stock folder adds nothing to texts.yml and copies no file")
    void stockFolderKeepsNothing() {
        write(folder, "config.yml", "language: en_US_DonutSMP\n");
        file("language/en_US/messages.yml", "prefix: '&8[Mine] '\n");
        stockDonutFolder();

        migrate();

        assertFalse(Files.exists(folder.resolve("gui_layouts/DonutSMP/texts.yml")),
                "the layout updater extracts it later, verbatim");
        assertEquals("&8[Mine] ", read(folder, "language/en_US/messages.yml").getString("prefix"),
                "an unedited DonutSMP file does not overwrite en_US");
    }

    @Test
    @DisplayName("an unedited legacy value matches the same shipped text written in MiniMessage")
    void legacyValueMatchesMiniMessage() {
        assertTrue(LayoutTextsMigration.sameText("&#00F986SELL ALL", "<#00F986>SELL ALL"));
        assertTrue(LayoutTextsMigration.sameText(List.of("&7Sell everything"), List.of("<gray>Sell everything")));
        assertFalse(LayoutTextsMigration.sameText("&aSell", "<red>Sell"));
        assertTrue(LayoutTextsMigration.sameText("&#545454{ᴇɴᴛɪᴛʏ} ꜱᴘᴀᴡɴᴇʀ", "<#545454>{entity_small} <smallcaps>Spawner</smallcaps>"),
                "the small caps placeholder was renamed, which is no edit");
        assertFalse(LayoutTextsMigration.sameText("{ᴇɴᴛɪᴛʏ}", "{entity}"));
    }

    @Test
    @DisplayName("edited GUI texts move into the layout's texts.yml, on top of the shipped ones")
    void editedTextsMove() {
        write(folder, "config.yml", "language: en_US\n");
        file("language/en_US_DonutSMP/gui.yml", """
                sell_button:
                  name: '&#00F986SELL ALL'
                  lore:
                    - '&eMy lore'
                return_button:
                  name: '&cBack'
                custom_key: 'mine'
                """);

        assertFalse(migrate(), "the active locale was en_US, so config.yml is untouched");

        YamlConfiguration texts = read(folder, "gui_layouts/DonutSMP/texts.yml");
        assertEquals("<#00F986>SELL ALL", texts.getString("sell_button.name"), "shipped text is still there");
        assertEquals(List.of("&eMy lore"), texts.getStringList("sell_button.lore"));
        assertEquals("mine", texts.getString("custom_key"), "an unknown key is kept");
        assertFalse(texts.contains("return_button"), "a text equal to en_US is not copied");
    }

    @Test
    @DisplayName("an edited text overwrites the shipped one in an existing texts.yml")
    void editedTextOverridesShipped() {
        write(folder, "config.yml", "language: en_US\n");
        file("gui_layouts/DonutSMP/texts.yml", DONUT_TEXTS);
        file("language/en_US_DonutSMP/gui.yml", "sell_button:\n  name: '&dMine'\n");

        migrate();

        assertEquals("&dMine", read(folder, "gui_layouts/DonutSMP/texts.yml").getString("sell_button.name"));
    }

    @Test
    @DisplayName("edited messages of the active DonutSMP locale replace en_US's")
    void editedMessagesOfActiveLocaleAreKept() {
        write(folder, "config.yml", "language: en_US_DonutSMP\n");
        file("language/en_US/messages.yml", BASE_MESSAGES);
        file("language/en_US_DonutSMP/gui.yml", BASE_GUI);
        file("language/en_US_DonutSMP/messages.yml", """
                prefix: '&8[SS] '
                no_exp:
                  action_bar: 'No exp here'
                """);

        migrate();

        YamlConfiguration messages = read(folder, "language/en_US/messages.yml");
        assertEquals("No exp here", messages.getString("no_exp.action_bar"));
        assertFalse(messages.contains("no_exp.message"), "a removed component stays removed");
    }

    @Test
    @DisplayName("the language and layout folders are backed up first, and a backup is never overwritten")
    void backupIsWritten() throws Exception {
        write(folder, "config.yml", "language: en_US\n");
        stockDonutFolder();
        file("gui_layouts/default/main_gui.yml", "skip_main_gui: false\n");
        Files.createDirectories(folder.resolve("language.backup-2026-11-02"));

        migrate();

        Path backup = folder.resolve("language.backup-2026-11-02-2");
        assertTrue(Files.exists(backup.resolve("language/en_US_DonutSMP/gui.yml")));
        assertTrue(Files.exists(backup.resolve("gui_layouts/default/main_gui.yml")));
    }

    @Test
    @DisplayName("a second run finds nothing and changes nothing")
    void secondRunIsNoOp() {
        write(folder, "config.yml", "language: en_US_DonutSMP\n");
        stockDonutFolder();
        migrate();

        assertFalse(migrate());
        assertFalse(Files.exists(folder.resolve("language.backup-2026-11-02-2")), "no second backup");
    }

    @Test
    @DisplayName("a server without DonutSMP folders is not touched")
    void unrelatedServerIsUntouched() {
        write(folder, "config.yml", "language: vi_VN\n");
        file("language/vi_VN/gui.yml", BASE_GUI);

        assertFalse(migrate());
        assertFalse(Files.exists(folder.resolve("language.backup-2026-11-02")));
    }
}
