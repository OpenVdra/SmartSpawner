package github.nighter.smartspawner.spawner.gui.storage.button;

import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.utils.LRUCache;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class NavigationButtonCache {
    private static final int CACHE_SIZE = 512;

    private final LRUCache<Integer, ItemStack> previousButtons = new LRUCache<>(CACHE_SIZE);
    private final LRUCache<Integer, ItemStack> nextButtons = new LRUCache<>(CACHE_SIZE);
    private final Function<ButtonData, ItemStack> buttonFactory;

    private String previousButtonName;
    private String nextButtonName;
    private List<String> previousButtonLore = Collections.emptyList();
    private List<String> nextButtonLore = Collections.emptyList();
    private Material previousButtonMaterial;
    private Material nextButtonMaterial;

    public NavigationButtonCache(Function<ButtonData, ItemStack> buttonFactory) {
        this.buttonFactory = buttonFactory;
    }

    public void reload(GuiLayout layout, LanguageManager languageManager) {
        clear();
        previousButtonName = languageManager.getGuiItemName("navigation_button_previous.name");
        nextButtonName = languageManager.getGuiItemName("navigation_button_next.name");
        previousButtonLore = languageManager.getGuiItemLoreAsList("navigation_button_previous.lore");
        nextButtonLore = languageManager.getGuiItemLoreAsList("navigation_button_next.lore");
        previousButtonMaterial = null;
        nextButtonMaterial = null;

        for (GuiButton button : layout.getAllButtons().values()) {
            String action = getAnyActionFromButton(button);
            if (action == null) {
                continue;
            }

            switch (action) {
                case "previous_page" -> previousButtonMaterial = button.getMaterial();
                case "next_page" -> nextButtonMaterial = button.getMaterial();
            }
        }
    }

    public ItemStack getPreviousButton(int targetPage) {
        return previousButtons.get(targetPage, this::createPreviousButton);
    }

    public ItemStack getNextButton(int targetPage) {
        return nextButtons.get(targetPage, this::createNextButton);
    }

    public void clear() {
        previousButtons.clear();
        nextButtons.clear();
    }

    private ItemStack createPreviousButton(int targetPage) {
        return createButton(previousButtonMaterial, previousButtonName, previousButtonLore, targetPage);
    }

    private ItemStack createNextButton(int targetPage) {
        return createButton(nextButtonMaterial, nextButtonName, nextButtonLore, targetPage);
    }

    private ItemStack createButton(Material material, String name, List<String> lore, int targetPage) {
        String targetPageText = String.valueOf(targetPage);
        return buttonFactory.apply(
                new ButtonData(
                        material,
                        replaceTargetPage(name, targetPageText),
                        replaceTargetPage(lore, targetPageText)
                )
        );
    }

    private String replaceTargetPage(String text, String targetPage) {
        return text != null ? text.replace("{target_page}", targetPage) : null;
    }

    private List<String> replaceTargetPage(List<String> lore, String targetPage) {
        if (lore.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> replacedLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            replacedLore.add(line.replace("{target_page}", targetPage));
        }
        return replacedLore;
    }

    private String getAnyActionFromButton(GuiButton button) {
        Map<String, String> actions = button.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        String action = actions.get("click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        action = actions.get("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }

    public record ButtonData(Material material, String name, List<String> lore) {}
}
