package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.language.file.LegacyLanguageKeys;
import github.nighter.smartspawner.language.format.TextFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.readRaw;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.8.0 move from eight language files per locale to four, with renamed keys. Runs against the real
 * shipped files, and the 1.7 en_US files kept under {@code fixtures/language-v1}.
 */
class LanguageRestructureTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final List<String> OLD_FILES = List.of("messages.yml", "command_messages.yml", "gui.yml",
            "command_gui.yml", "items.yml", "hologram.yml", "formatting.yml", "iridium_skyblock.yml");
    private static final Function<String, InputStream> RESOURCES =
            path -> LanguageRestructureTest.class.getClassLoader().getResourceAsStream(path);

    @TempDir
    Path folder;

    private int migrate() {
        Supplier<String> backup = LanguageBackup.once(folder.toFile(), TODAY, silentLogger());
        return LanguageRestructure.migrate(folder.toFile(), RESOURCES, backup, silentLogger());
    }

    /** The 1.7 en_US files, as shipped, in language/<locale>/. */
    private void stockOldLocale(String locale) throws IOException {
        Path target = folder.resolve("language/" + locale);
        Files.createDirectories(target);
        for (String file : OLD_FILES) {
            try (InputStream stream = RESOURCES.apply("fixtures/language-v1/en_US/" + file)) {
                Files.copy(stream, target.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void file(String path, String content) {
        try {
            Files.createDirectories(folder.resolve(path).getParent());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        write(folder, path, content);
    }

    private static YamlConfiguration fixture(String file) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream stream = RESOURCES.apply("fixtures/language-v1/en_US/" + file)) {
            config.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return config;
    }

    @Test
    @DisplayName("a stock 1.7 folder ends up with four files, every old value under its new name")
    void stockFolderMigrates() throws IOException {
        stockOldLocale("en_US");

        assertEquals(1, migrate());

        File dir = folder.resolve("language/en_US").toFile();
        for (String gone : List.of("command_messages.yml", "command_gui.yml", "hologram.yml", "iridium_skyblock.yml")) {
            assertFalse(new File(dir, gone).exists(), gone + " is merged away");
        }
        assertTrue(new File(dir, "hooks/iridium_skyblock.yml").isFile());

        for (LegacyLanguageKeys.OldFile old : LegacyLanguageKeys.ALL) {
            YamlConfiguration before = fixture(old.name());
            YamlConfiguration after = read(folder, "language/en_US/" + old.target().getFileName());
            for (String path : before.getKeys(true)) {
                if (before.isConfigurationSection(path)) continue;
                String target = LegacyLanguageKeys.moveOrKeep(old, path);
                assertTrue(sameText(before.get(path), after.get(target)),
                        old.name() + ":" + path + " -> " + target + " was " + before.get(path) + ", now " + after.get(target));
            }
        }
        assertTrue(readRaw(folder, "language/en_US/messages.yml").startsWith("# SmartSpawner messages"),
                "the new file carries the shipped header");
    }

    @Test
    @DisplayName("edited values in every old file are kept under their new names")
    void editedValuesAreKept() throws IOException {
        stockOldLocale("en_US");
        file("language/en_US/messages.yml", "prefix: '&8[Mine] '\nno_exp:\n  message: '&cNo exp, sorry'\n");
        file("language/en_US/command_messages.yml", "list:\n  no_spawners_found:\n    message: 'Nothing here'\n");
        file("language/en_US/gui.yml", "sell_button:\n  name: '&aSell!'\n");
        file("language/en_US/command_gui.yml", "world_buttons:\n  NETHER:\n    name: 'Hot {world_name}'\n");
        file("language/en_US/hologram.yml", "hologram_text:\n  - 'My {entity}'\n");

        migrate();

        YamlConfiguration messages = read(folder, "language/en_US/messages.yml");
        assertEquals("&8[Mine] ", messages.getString("prefix"));
        assertEquals("&cNo exp, sorry", messages.getString("exp.none.message"));
        assertEquals("Nothing here", messages.getString("command.list.no_spawners_found.message"));
        YamlConfiguration gui = read(folder, "language/en_US/gui.yml");
        assertEquals("&aSell!", gui.getString("storage.sell.name"));
        assertEquals("Hot {world_name}", gui.getString("command.list.worlds.buttons.NETHER.name"));
        assertEquals(List.of("My {entity}"), read(folder, "language/en_US/items.yml").getStringList("hologram.lines"));
    }

    @Test
    @DisplayName("a message whose component the owner removed stays without it")
    void removedComponentStaysRemoved() throws IOException {
        stockOldLocale("en_US");
        file("language/en_US/messages.yml", "no_exp:\n  action_bar: 'No exp'\n");

        migrate();

        YamlConfiguration messages = read(folder, "language/en_US/messages.yml");
        assertEquals("No exp", messages.getString("exp.none.action_bar"));
        assertFalse(messages.contains("exp.none.message"), "the shipped chat line does not come back");
        assertTrue(messages.contains("exp.collected.message"), "other messages come from the template");
    }

    @Test
    @DisplayName("a custom locale with a few keys and an unknown one is renamed on en_US's template")
    void customLocale() {
        file("language/de_DE/messages.yml", "no_exp:\n  message: 'Keine EXP'\nmy_own_message:\n  message: 'Hallo'\n");
        file("language/de_DE/command_messages.yml", "reload:\n  success:\n    message: 'Neu geladen'\nmystery: 'keep me'\n");

        migrate();

        YamlConfiguration messages = read(folder, "language/de_DE/messages.yml");
        assertEquals("Keine EXP", messages.getString("exp.none.message"));
        assertEquals("Neu geladen", messages.getString("command.reload.success.message"));
        assertEquals("Hallo", messages.getString("my_own_message.message"), "an unknown key keeps its path");
        assertEquals("keep me", messages.getString("command.mystery"), "an unknown command key moves under command");
        assertTrue(messages.contains("sell.success.message"), "the rest comes from en_US");
        assertFalse(new File(folder.toFile(), "language/de_DE/command_messages.yml").exists());
    }

    @Test
    @DisplayName("in a half-migrated folder the value already at the new path wins")
    void newLayoutWins() {
        file("language/en_US/messages.yml", "exp:\n  none:\n    message: 'new one'\n");
        file("language/en_US/command_messages.yml", "list:\n  no_spawners_found:\n    message: 'old list'\n");
        file("language/en_US/gui.yml", "sell_button:\n  name: 'old'\nstorage:\n  sell:\n    name: 'new'\n");

        migrate();

        assertEquals("new one", read(folder, "language/en_US/messages.yml").getString("exp.none.message"));
        assertEquals("old list", read(folder, "language/en_US/messages.yml").getString("command.list.no_spawners_found.message"));
        assertEquals("new", read(folder, "language/en_US/gui.yml").getString("storage.sell.name"));
    }

    @Test
    @DisplayName("an owner who changed only the storage title keeps it on single spawners too")
    void singleStorageTitleFollowsTitle() {
        file("language/en_US/gui.yml", "gui_title_storage: 'My storage'\n");

        migrate();

        YamlConfiguration gui = read(folder, "language/en_US/gui.yml");
        assertEquals("My storage", gui.getString("storage.title"));
        assertEquals("My storage", gui.getString("storage.title_single"));
    }

    @Test
    @DisplayName("items.yml keeps the owner's mob sections exactly, without adding shipped ones")
    void itemsKeepInheritance() {
        file("language/en_US/items.yml", "smart_spawner:\n  default:\n    name: 'S {entity}'\n  ZOMBIE:\n    name: 'Z'\n");
        file("language/en_US/hologram.yml", "hologram_text:\n  - 'H'\n");

        migrate();

        YamlConfiguration items = read(folder, "language/en_US/items.yml");
        assertEquals("Z", items.getString("smart_spawner.ZOMBIE.name"));
        assertNull(items.get("smart_spawner.PIG"), "the shipped PIG section is not added");
        assertNull(items.get("smart_spawner.ZOMBIE.lore"), "a mob section keeps inheriting its lore");
        assertEquals(List.of("H"), items.getStringList("hologram.lines"));
    }

    @Test
    @DisplayName("a layout's texts.yml gets the same GUI renames")
    void layoutTextsAreRenamed() {
        file("language/en_US/messages.yml", "prefix: 'x'\n");
        file("gui_layouts/DonutSMP/texts.yml", "# My texts\n\nsell_button:\n  name: 'Sell'\ngui_title_main:\n  single_spawner: 'One'\n");

        migrate();

        YamlConfiguration texts = read(folder, "gui_layouts/DonutSMP/texts.yml");
        assertEquals("Sell", texts.getString("storage.sell.name"));
        assertEquals("One", texts.getString("main.title"));
        assertFalse(texts.contains("sell_button"));
        assertTrue(readRaw(folder, "gui_layouts/DonutSMP/texts.yml").startsWith("# My texts"));
    }

    @Test
    @DisplayName("everything is backed up once, and a second run changes nothing")
    void backupThenNoOp() throws IOException {
        stockOldLocale("en_US");
        stockOldLocale("vi_VN");

        assertEquals(2, migrate());
        Path backup = folder.resolve("language.backup-2026-11-02");
        assertTrue(Files.exists(backup.resolve("language/en_US/command_messages.yml")));
        assertTrue(Files.exists(backup.resolve("language/vi_VN/hologram.yml")));

        String messages = readRaw(folder, "language/en_US/messages.yml");
        assertEquals(0, migrate());
        assertEquals(messages, readRaw(folder, "language/en_US/messages.yml"));
        assertFalse(Files.exists(folder.resolve("language.backup-2026-11-02-2")), "no second backup");
    }

    @Test
    @DisplayName("a folder already in the new layout is not touched")
    void newFolderIsUntouched() {
        file("language/en_US/messages.yml", "general:\n  inventory_full:\n    message: 'Full'\n");
        file("language/en_US/gui.yml", "main:\n  title: 'T'\n");

        assertEquals(0, migrate());
        assertFalse(Files.exists(folder.resolve("language.backup-2026-11-02")));
    }

    @Test
    @DisplayName("an old DonutSMP locale is restructured, then folded into the layout's texts.yml")
    void donutLocaleThroughBothMigrations() throws IOException {
        write(folder, "config.yml", "language: en_US_DonutSMP\ngui_layout: default\n");
        stockOldLocale("en_US_DonutSMP");
        file("language/en_US_DonutSMP/gui.yml", "sell_button:\n  name: '&aMy sell'\n");

        Supplier<String> backup = LanguageBackup.once(folder.toFile(), TODAY, silentLogger());
        LanguageRestructure.migrate(folder.toFile(), RESOURCES, backup, silentLogger());
        assertTrue(LayoutTextsMigration.migrate(folder.toFile(), RESOURCES, backup, silentLogger()));

        assertEquals("DonutSMP", read(folder, "config.yml").getString("gui_layout"));
        assertEquals("&aMy sell", read(folder, "gui_layouts/DonutSMP/texts.yml").getString("storage.sell.name"));
        assertFalse(Files.exists(folder.resolve("language/en_US_DonutSMP")));
        assertFalse(Files.exists(folder.resolve("language.backup-2026-11-02-2")), "both share one backup");
    }

    private static boolean sameText(Object a, Object b) {
        if (a instanceof String first && b instanceof String second) {
            return TextFormatter.toLegacy(first, Map.of()).equals(TextFormatter.toLegacy(second, Map.of()));
        }
        if (a instanceof List<?> first && b instanceof List<?> second) {
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) {
                if (!sameText(first.get(i), second.get(i))) return false;
            }
            return true;
        }
        return java.util.Objects.equals(a, b);
    }
}
