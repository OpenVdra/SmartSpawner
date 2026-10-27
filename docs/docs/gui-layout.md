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
│   ├── ...
│   └── texts.yml
└── DonutSMP_v2/
    └── ...
```

Select the active layout with `gui_layout` in `config.yml`, or switch it live using `/ss gui_layout <name>`.

## Layout Texts

A layout can have a `texts.yml` that replaces GUI names, lore and titles from `language/<locale>/gui.yml`. It uses the same keys as `gui.yml` and only lists the ones it changes. `DonutSMP` and `DonutSMP_v2` use it for their look, so picking the layout is all it takes.

Delete a key from `texts.yml` to use the language's text again. A layout without `texts.yml` uses the language's text everywhere. The texts in `texts.yml` are not translated, so they show the same with every `language`.

## How Your Edits Are Kept

The buttons in a layout file belong to you. Move one to another slot or delete it, and it stays that way through restarts and `/ss reload`. Nothing is ever put back at the old slot.

The trade is that a button added in a future update will not appear in a file you already have. Check the changelog when you update and copy the new button in by hand if you want it. Settings that are not buttons, such as `skip_main_gui` and `open_sound`, are still added for you.

To start a file over, delete it and restart the server. It comes back exactly as it ships, comments included.

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
- All action buttons have a built-in **100 ms anti-spam debounce**, even without a `cooldown`.
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
        action: "none"
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
  action: "collect_exp"
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
        action: "none"
    no_sell_integration:
      click:
        action: "none"
```

- `custom_texture` is the hash from the texture URL (without the `http://textures.minecraft.net/texture/` prefix).
- Textures can be found at [Minecraft-Heads.com](https://minecraft-heads.com/).
- If omitted on the info button, the plugin falls back to the mob head from `spawner_mobs.yml`.
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
        action: "none"
```

Available conditions include `sell_integration` and `no_sell_integration`.
