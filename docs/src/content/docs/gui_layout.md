---
title: GUI Layout Configuration
description: How to customize spawner GUIs, including custom player head textures
---

SmartSpawner allows you to fully customize the appearance and behavior of spawner GUIs using layout files. These files are located in the `plugins/SmartSpawner/gui_layouts/` directory.

## Directory Structure

```text
plugins/SmartSpawner/gui_layouts/
├── default/
│   ├── main_gui.yml
│   ├── storage_gui.yml
│   └── sell_confirm_gui.yml
├── DonutSMP/
│   └── ...
└── DonutSMP_v2/
    └── ...
```

You can select which layout to use by changing the `gui_layout` value in your main `config.yml` file.

## Custom Player Head Textures

If a button uses `PLAYER_HEAD` as its material, you can provide a `custom_texture` to display a specific skin. This is especially useful for the spawner info button or any custom decorative buttons, working exactly like the `head_texture` configuration in `spawners_settings.yml`.

### Example Configuration

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
  enabled: true
  info_button: true
  if:
    sell_integration:
      left_click: "sell_and_exp"
      right_click: "open_stacker"
    no_sell_integration:
      click: "open_stacker"
```

### Key Details

- **`custom_texture`**: The texture hash string (without the `http://textures.minecraft.net/texture/` prefix). You can find these hashes on sites like [Minecraft Heads](https://minecraft-heads.com/).
- **Scope**: This setting works for **any** button in `main_gui.yml` or `storage_gui.yml` that uses `PLAYER_HEAD`.
- **Fallback Behavior**: If `custom_texture` is omitted for the spawner info button, the plugin will automatically fall back to the default mob head texture defined for that entity in `spawners_settings.yml`.
- **Performance**: Custom texture heads are heavily cached by the plugin after the first load, ensuring zero performance impact when players repeatedly open the GUI.

## Conditional Overrides

You can also dynamically change the button's `material` and `actions` based on server conditions using the `if:` block. 

:::note[Custom Texture Limitation]
The `custom_texture` property **only works when `material` is set to `PLAYER_HEAD`**. If you override the material to something else (e.g., `EMERALD`) inside an `if:` block, the `custom_texture` will be ignored.
:::

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "default_texture_hash_here"
  enabled: true
  info_button: true
  if:
    sell_integration:
      material: EMERALD # custom_texture is ignored here because material is no longer PLAYER_HEAD
      left_click: "sell_and_exp"
    no_sell_integration:
      click: "open_stacker"
```

<br>
<br>

---

*Last update: June 9, 2026*