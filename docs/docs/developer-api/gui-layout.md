# GUI Layout API

SmartSpawner allows external plugins to register and override GUI layouts at runtime.

## Two Approaches

1. **Global Layout Registration**: Register a named layout that admins can select via `gui_layout` in `config.yml`.
2. **Per-Spawner Layout Provider**: Dynamically choose a layout per spawner/player combination.

---

## Global Layout Registration

### Methods

| Method | Returns |
|--------|---------|
| `getLayoutRegistry()` | `GuiLayoutRegistry` |
| `registerLayout(name, main, storage, sellConfirm)` | `boolean` |
| `registerLayoutFromYaml(name, main, storage, sellConfirm)` | `boolean` |
| `unregisterLayout(String)` | `boolean` |
| `isRegistered(String)` | `boolean` |
| `getRegisteredLayouts()` | `Set<String>` |

### Programmatic Layout

```java
GuiLayoutData mainGui = new GuiLayoutBuilder()
    .type(GuiLayoutType.MAIN_GUI)
    .addButton("slot_11", new GuiButtonBuilder()
        .slot(11)
        .material(Material.CHEST)
        .cooldown("2s")
        .action("click", "open_storage")
        .sound("block.chest.open", 1.0f, 1.0f)
        .build())
    .addButton("slot_15", new GuiButtonBuilder()
        .slot(15)
        .material(Material.EXPERIENCE_BOTTLE)
        .action("click", "collect_exp")
        .build())
    .build();

api.getLayoutRegistry().registerLayout("myplugin_custom", mainGui, null, null);
```

After registration, admins can select it in `config.yml`:

```yaml
gui_layout: myplugin_custom
```

### Layout from YAML Files

```java
File main = new File(getDataFolder(), "layouts/main_gui.yml");
File storage = new File(getDataFolder(), "layouts/storage_gui.yml");
File sell = new File(getDataFolder(), "layouts/sell_confirm_gui.yml");

api.getLayoutRegistry().registerLayoutFromYaml("myplugin_custom", main, storage, sell);
```

---

## Per-Spawner Layout Provider

Dynamically assign layouts per spawner and player.

```java
api.setSpawnerLayoutProvider(new SpawnerGuiLayoutProvider() {

    @Override
    public GuiLayoutData getLayout(SpawnerDataDTO spawner, Player player, GuiLayoutType type) {
        if (type == GuiLayoutType.MAIN_GUI && isPremiumPlayer(player)) {
            return premiumMainGui;
        }
        return null; // Fall back to default resolution
    }

    @Override
    public String getProviderName() {
        return "MyPlugin";
    }
});
```

Return `null` to fall back to default layout resolution:
1. Per-spawner provider
2. Global registered layout
3. File-based layout in `gui_layouts/`

To remove the provider: `api.clearSpawnerLayoutProvider()`.

---

## GuiLayoutType Enum

```java
public enum GuiLayoutType {
    MAIN_GUI,
    STORAGE_GUI,
    SELL_CONFIRM_GUI
}
```

---

## Available Button Actions

| Action | Description |
|--------|-------------|
| `open_storage` | Open spawner storage GUI |
| `open_stacker` | Open spawner stacker GUI |
| `collect_exp` | Claim stored XP |
| `sell_and_exp` | Sell items and claim XP |
| `sell_all` | Sell all items |
| `take_all` | Take all items from storage |
| `sort_items` | Sort/filter items |
| `drop_page` | Drop all items on current page |
| `previous_page` / `next_page` | Navigate storage pages |
| `return_main` | Return to main GUI |
| `confirm` / `cancel` | Sell confirmation |
| `none` | Display-only (no action) |

Click types: `click`, `left_click`, `right_click`, `shift_left_click`, `shift_right_click`

---

## Notes

- `GuiLayoutData` and `GuiButtonData` are **immutable** and thread-safe.
- Slot numbers are 1-based (matching YAML layout files).
- Storage GUI: storage control row uses slots `1–9` mapped to inventory rows `45–53`.
- Per-player providers must keep slot positions consistent across players for the same spawner (item materials and actions can vary, but not slot positions).
