package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiLayoutUpdater {
    private static final String VERSION_KEY = "gui_layout_version";
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES  = {"storage_gui.yml", "main_gui.yml", "sell_confirm_gui.yml"};
    private static final String[] LAYOUT_NAMES  = {"default", "DonutSMP", "DonutSMP_v2"};
    private static final Pattern LEGACY_CLICK_PATH = Pattern.compile(
            "^(slot_[^.]+(?:\\.if\\.[^.]+)?)\\."
                    + "(click|left_click|right_click|shift_left_click|shift_right_click)$");
    private static final Pattern LEGACY_SOUND_PATH = Pattern.compile(
            "^(slot_[^.]+(?:\\.if\\.[^.]+)?)\\.sound(?:\\.(success|fail))?(.*)$");

    private final SmartSpawner plugin;

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if GUI layouts need to be updated and update them if necessary
     */
    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        layoutsDir.mkdirs();

        for (String layoutName : LAYOUT_NAMES) {
            File layoutDir = new File(layoutsDir, layoutName);
            layoutDir.mkdirs();

            for (String fileName : LAYOUT_FILES) {
                File dataFile    = new File(layoutDir, fileName);
                String resource  = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
                ConfigVersionService.updateFile(
                        plugin, dataFile, resource, VERSION_KEY, this::migrateLayoutPaths);
            }
        }
    }

    private void migrateLayoutPaths(Map<String, Object> userValues) {
        for (String oldPath : new ArrayList<>(userValues.keySet())) {
            Matcher clickMatcher = LEGACY_CLICK_PATH.matcher(oldPath);
            if (clickMatcher.matches()) {
                move(userValues, oldPath,
                        clickMatcher.group(1) + "." + clickMatcher.group(2) + ".action");
                continue;
            }

            Matcher soundMatcher = LEGACY_SOUND_PATH.matcher(oldPath);
            if (!soundMatcher.matches()) {
                continue;
            }

            String soundKey = switch (soundMatcher.group(2)) {
                case "success" -> "sound_success";
                case "fail" -> "sound_fail";
                case null -> "sound";
                default -> "sound";
            };
            move(userValues, oldPath, soundMatcher.group(1) + ".click." + soundKey
                    + soundMatcher.group(3));
        }
    }

    private void move(Map<String, Object> values, String oldPath, String newPath) {
        if (!values.containsKey(newPath)) {
            values.put(newPath, values.get(oldPath));
        }
        values.remove(oldPath);
    }
}
