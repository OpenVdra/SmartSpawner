package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the bundled GUI layout files in sync with the defaults. Version-less: on every startup it
 * migrates legacy click / sound paths and tops up missing keys, preserving user customisations and
 * comments. See {@link YamlMigrator} and {@link ConfigMigrations#GUI_LAYOUT}.
 *
 * <p>Buttons are the exception to the top-up. A button's slot is part of its key, so moving one
 * from {@code slot_15} to {@code slot_17} leaves {@code slot_15} looking merely absent, and topping
 * it back up would give the user two copies of the same button. Once a layout has any button at
 * all, its {@code slot_*} sections belong to the user; only the settings around them are topped
 * up.</p>
 *
 * <p>A layout's optional {@code texts.yml} overrides GUI texts from the locale. It is extracted and
 * renamed but never topped up: a key the owner deletes from it falls back to the locale on purpose.</p>
 */
public class GuiLayoutUpdater {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES  = {"storage_gui.yml", "main_gui.yml", "sell_confirm_gui.yml", "filter_gui.yml"};
    /** The layouts bundled in the jar. Only the active one and those already on disk are extracted. */
    public static final List<String> LAYOUT_NAMES = List.of("default", "DonutSMP", "DonutSMP_v2");
    private static final String TEXTS_FILE      = "texts.yml";
    private static final String BUTTON_PREFIX   = "slot_";

    /** Matches the top-level {@code slot_*} button sections, the ones whose key encodes a position. */
    private static final YamlMigrator.SectionMatcher BUTTON_SECTIONS =
            (defaults, path) -> path.startsWith(BUTTON_PREFIX) && !path.contains(".");

    private final SmartSpawner plugin;

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensure each GUI layout file exists and is up-to-date.
     */
    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        layoutsDir.mkdirs();

        for (String layoutName : layoutsInUse(layoutsDir)) {
            boolean bundled = LAYOUT_NAMES.contains(layoutName);
            File layoutDir = new File(layoutsDir, layoutName);
            // An addon layout registered through the API may have no folder at all; never create one for it.
            if (!bundled && !layoutDir.isDirectory()) continue;
            layoutDir.mkdirs();

            for (String fileName : LAYOUT_FILES) {
                File dataFile   = new File(layoutDir, fileName);
                if (!bundled && !dataFile.exists()) continue;
                String resource = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
                YamlMigrator.migrate(
                        dataFile,
                        plugin.getResource(resource),
                        List.of(),
                        ConfigMigrations.GUI_LAYOUT,
                        true,
                        hasAnyButton(dataFile) ? YamlMigrator.OwnedSection.userManaged(BUTTON_SECTIONS) : null,
                        plugin.getLogger());
            }

            String textsResource = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + TEXTS_FILE;
            InputStream texts = plugin.getResource(textsResource);
            if (texts != null) {
                YamlMigrator.migrate(new File(layoutDir, TEXTS_FILE), texts, ConfigMigrations.GUI,
                        null, false, plugin.getLogger());
            }
        }
    }

    /** The active layout plus every layout folder already on disk. */
    private Set<String> layoutsInUse(File layoutsDir) {
        Set<String> layouts = new LinkedHashSet<>();
        layouts.add(plugin.getConfig().getString("gui_layout", "default"));
        File[] folders = layoutsDir.listFiles(File::isDirectory);
        if (folders != null) {
            Arrays.stream(folders).map(File::getName).sorted().forEach(layouts::add);
        }
        return layouts;
    }

    /**
     * Whether {@code file} still defines at least one button.
     *
     * <p>A layout with no buttons left is not a layout the user is curating, it is one that would
     * open empty, so the defaults are written back into it. A button turned off with
     * {@code enabled: false} still counts: the user kept it, they just hid it.</p>
     *
     * <p>Deleting the file remains the cleaner way to start over. That path copies the bundled
     * resource verbatim, comments and all, where this one only refills the keys.</p>
     */
    private boolean hasAnyButton(File file) {
        if (!file.exists()) return false;
        for (String key : YamlConfiguration.loadConfiguration(file).getKeys(false)) {
            if (key.startsWith(BUTTON_PREFIX)) return true;
        }
        return false;
    }
}
