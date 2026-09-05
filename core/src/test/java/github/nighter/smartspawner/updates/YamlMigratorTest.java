package github.nighter.smartspawner.updates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.readRaw;
import static github.nighter.smartspawner.ConfigFixtures.resource;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version-less migration engine. Its contract is that it only ever adds: a value the user
 * already set is never touched, and the file is only written when something actually changed.
 */
class YamlMigratorTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("a missing file is extracted from the bundled resource verbatim")
    void firstInstallExtractsVerbatim() {
        String bundled = "# a header comment\nkey: value\n";
        File file = folder.resolve("new.yml").toFile();

        assertTrue(YamlMigrator.migrate(file, resource(bundled), List.of(), silentLogger()));

        assertTrue(file.exists());
        assertEquals(bundled, readRaw(folder, "new.yml"),
                "formatting and comments must survive a first install byte for byte");
    }

    @Test
    @DisplayName("a rename moves the user's value and drops the old key")
    void renameMovesTheUsersValue() {
        write(folder, "c.yml", "old:\n  path: 42\n");

        YamlMigrator.migrate(folder.resolve("c.yml").toFile(), null,
                List.of(new YamlMigrator.Rename("old.path", "new.path")), silentLogger());

        YamlConfiguration result = read(folder, "c.yml");
        assertEquals(42, result.getInt("new.path"));
        assertFalse(result.contains("old.path"));
    }

    @Test
    @DisplayName("a value already at the new key wins over the old one")
    void renameKeepsTheValueAlreadyAtTheNewKey() {
        write(folder, "c.yml", "old:\n  path: 42\nnew:\n  path: 7\n");

        YamlMigrator.migrate(folder.resolve("c.yml").toFile(), null,
                List.of(new YamlMigrator.Rename("old.path", "new.path")), silentLogger());

        YamlConfiguration result = read(folder, "c.yml");
        assertEquals(7, result.getInt("new.path"), "the user's newer value must not be overwritten");
        assertFalse(result.contains("old.path"));
    }

    @Test
    @DisplayName("chained renames hop through every intermediate name, in list order")
    void chainedRenamesReachTheCurrentName() {
        write(folder, "c.yml", "a: 1\n");

        YamlMigrator.migrate(folder.resolve("c.yml").toFile(), null, List.of(
                new YamlMigrator.Rename("a", "b"),
                new YamlMigrator.Rename("b", "c")), silentLogger());

        YamlConfiguration result = read(folder, "c.yml");
        assertEquals(1, result.getInt("c"));
        assertFalse(result.contains("a"));
        assertFalse(result.contains("b"));
    }

    @Test
    @DisplayName("missing keys are added from the defaults, carrying their comments")
    void missingKeysAreAddedWithComments() {
        write(folder, "c.yml", "existing: mine\n");
        String bundled = "existing: theirs\n# explains the new key\nadded: 5\n";

        YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource(bundled), List.of(), silentLogger());

        YamlConfiguration result = read(folder, "c.yml");
        assertEquals("mine", result.getString("existing"), "an existing value is never overwritten");
        assertEquals(5, result.getInt("added"));
        assertEquals(List.of("explains the new key"), result.getComments("added"));
    }

    @Test
    @DisplayName("the legacy version keys are stripped")
    void legacyVersionKeysAreStripped() {
        write(folder, "c.yml", "config_version: 12\nlanguage_version: 3\ngui_layout_version: 1\nkeep: yes\n");

        YamlMigrator.migrate(folder.resolve("c.yml").toFile(), null, List.of(), silentLogger());

        YamlConfiguration result = read(folder, "c.yml");
        assertFalse(result.contains("config_version"));
        assertFalse(result.contains("language_version"));
        assertFalse(result.contains("gui_layout_version"));
        assertTrue(result.contains("keep"));
    }

    @Test
    @DisplayName("a file that needs nothing is left untouched on disk")
    void nothingToDoLeavesTheFileAlone() {
        String original = "# hand written\nkey:   value\n\n\n";
        write(folder, "c.yml", original);

        assertFalse(YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource("key: value\n"),
                List.of(), silentLogger()));

        assertEquals(original, readRaw(folder, "c.yml"),
                "an unchanged file must not be rewritten, or hand formatting is lost on every start");
    }

    @Nested
    @DisplayName("owned sections")
    class OwnedSections {

        private static final YamlMigrator.SectionMatcher LOOT = (defaults, path) -> path.endsWith(".loot");

        @Test
        @DisplayName("contents of a section the user has are never topped up")
        void userHeldSectionIsNotToppedUp() {
            write(folder, "c.yml", "ZOMBIE:\n  loot:\n    rotten_flesh:\n      chance: 50.0\n");
            String bundled = """
                    ZOMBIE:
                      loot:
                        rotten_flesh:
                          chance: 100.0
                        iron_ingot:
                          chance: 2.5
                    """;

            YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource(bundled), List.of(), null,
                    true, YamlMigrator.OwnedSection.curated(LOOT), silentLogger());

            YamlConfiguration result = read(folder, "c.yml");
            assertEquals(50.0, result.getDouble("ZOMBIE.loot.rotten_flesh.chance"));
            assertFalse(result.contains("ZOMBIE.loot.iron_ingot"),
                    "a drop the owner deleted must not come back, and a relabelled one must not double up");
        }

        @Test
        @DisplayName("a curated section the user deleted stays deleted while its parent remains")
        void curatedSectionStaysDeleted() {
            write(folder, "c.yml", "ZOMBIE:\n  experience: 5\n");
            String bundled = "ZOMBIE:\n  experience: 5\n  loot:\n    rotten_flesh:\n      chance: 100.0\n";

            YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource(bundled), List.of(), null,
                    true, YamlMigrator.OwnedSection.curated(LOOT), silentLogger());

            assertFalse(read(folder, "c.yml").contains("ZOMBIE.loot"));
        }

        @Test
        @DisplayName("a curated section is written in full when its parent is new too")
        void curatedSectionArrivesWholeForANewParent() {
            write(folder, "c.yml", "ZOMBIE:\n  experience: 5\n");
            String bundled = """
                    ZOMBIE:
                      experience: 5
                    BOGGED:
                      experience: 6
                      loot:
                        arrow:
                          chance: 100.0
                        bone:
                          chance: 50.0
                    """;

            YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource(bundled), List.of(), null,
                    true, YamlMigrator.OwnedSection.curated(LOOT), silentLogger());

            YamlConfiguration result = read(folder, "c.yml");
            assertEquals(100.0, result.getDouble("BOGGED.loot.arrow.chance"));
            assertEquals(50.0, result.getDouble("BOGGED.loot.bone.chance"),
                    "a brand new section must arrive whole, not half filled");
        }

        @Test
        @DisplayName("a restoredWhenAbsent section the user deleted is written back")
        void restoredWhenAbsentComesBack() {
            write(folder, "c.yml", "other: 1\n");
            String bundled = "other: 1\nno_permission:\n  message: '&cDenied'\n";

            YamlMigrator.migrate(folder.resolve("c.yml").toFile(), resource(bundled), List.of(), null, true,
                    YamlMigrator.OwnedSection.restoredWhenAbsent((d, p) -> p.equals("no_permission")),
                    silentLogger());

            assertEquals("&cDenied", read(folder, "c.yml").getString("no_permission.message"),
                    "a message key looked up by name must be refilled, not honoured as a deletion");
        }
    }
}
