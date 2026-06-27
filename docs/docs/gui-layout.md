# GUI Layout

SmartSpawner allows full customization of its spawner GUIs through layout files located in `plugins/SmartSpawner/gui_layouts/`.

## Directory Structure

```
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

Select the active layout with `gui_layout` in `config.yml`, or switch it live using `/ss gui_layout <name>`.

## Button Cooldowns

Every button supports an optional per-player cooldown:

```yaml
slot_12:
  material: CHEST
  enabled: true
  cooldown: "2s"
  click:
    action: "open_storage"
```

The value uses the same time format as `config.yml` (`20`, `5s`, `10m`, `1h`, compound `1m_30s`, etc.).

- A button rejected by cooldown does not run its action, and sends the `action_not_ready` message with the `{time}` placeholder.
- All action buttons have a built-in **300 ms anti-spam debounce**, even without a `cooldown`.
- Cooldown can also be overridden inside a conditional `if:` branch.

```yaml
slot_14:
  material: PLAYER_HEAD
  enabled: true
  if:
    sell_integration:
      cooldown: "3s"
      click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "open_stacker"
```

## Button Click Sounds

Navigation buttons use a single `sound` played when navigation succeeds:

```yaml
slot_1:
  material: ARROW
  enabled: true
  click:
    action: "previous_page"
    sound: ui.button.click
```

Action buttons use separate sounds for success and failure:

```yaml
slot_6:
  material: BUNDLE
  enabled: true
  click:
    action: "take_all"
    sound_success:
      name: entity.item.pickup
      volume: 1.0
      pitch: 1.0
    sound_fail: block.note_block.pling
```

Left and right clicks can each have their own action and sounds:

```yaml
left_click:
  action: "sell_and_exp"
  sound: ui.button.click
  sound_success: block.note_block.bell
  sound_fail: block.note_block.pling
right_click:
  action: "open_stacker"
  sound: ui.button.click
```

Each sound key accepts a single Bukkit key string or a list with optional `volume` and `pitch`:

```yaml
sound_success:
  - block.note_block.bell
  - name: entity.experience_orb.pickup
    volume: 0.8
    pitch: 1.2
```

Set a sound key to `none` to disable it.

## Custom Player Head Textures

Any button with `material: PLAYER_HEAD` can display a custom skin texture via `custom_texture`:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
  enabled: true
  info_button: true
  if:
    sell_integration:
      left_click:
        action: "sell_and_exp"
      right_click:
        action: "open_stacker"
    no_sell_integration:
      click:
        action: "open_stacker"
```

- `custom_texture` is the hash from the texture URL (without the `http://textures.minecraft.net/texture/` prefix).
- Textures can be found at [Minecraft-Heads.com](https://minecraft-heads.com/).
- If omitted on the info button, the plugin falls back to the mob head from `spawners_settings.yml`.
- Textures are cached after first load with no performance impact on repeated GUI opens.

::: warning
`custom_texture` is ignored when `material` is overridden to something other than `PLAYER_HEAD` inside an `if:` branch.
:::

## Conditional Overrides

Use `if:` blocks to change button appearance or behavior based on server state:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "default_texture_hash_here"
  enabled: true
  info_button: true
  if:
    sell_integration:
      material: EMERALD   # custom_texture ignored — material is no longer PLAYER_HEAD
      left_click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "open_stacker"
```

Available conditions include `sell_integration` and `no_sell_integration`.
