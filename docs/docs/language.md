---
title: Language Files
---

# Language Files

Every message, menu text and item name comes from the files in `plugins/SmartSpawner/language/<language>/`. Pick the language with `language` in `config.yml`, or switch it live with `/ss language <name>`.

Only the active language is written to the plugin folder. Another built-in language is added the first time it is selected.

## Files

| File | Contains |
|------|----------|
| `messages.yml` | Chat messages, titles, action bars and sounds. Command messages are under `command`. |
| `gui.yml` | Menu titles and buttons, grouped by menu: `main`, `storage`, `sell_confirm`, `filter`. Menus opened by commands are under `command`. |
| `items.yml` | Spawner item names and lore, and the hologram lines under `hologram`. |
| `formatting.yml` | Number formats and mob names. |
| `hooks/iridium_skyblock.yml` | IridiumSkyblock permission names. Only written while that plugin is installed. |

A key missing from a file falls back to the same key in English, so a partly translated language still shows every text.

A message is a group of parts: `message` (chat), `title`, `subtitle`, `action_bar` and `sound`. Remove a part to stop sending it, or set `enabled: false` to mute the whole message.

## Colours and Styles

Every text accepts MiniMessage, the old `&` codes, or both in the same line.

| Style | MiniMessage | Legacy |
|-------|-------------|--------|
| Named colour | `<red>Text` | `&cText` |
| Hex colour | `<#5DB5F1>Text` | `&#5DB5F1Text` |
| Gradient | `<gradient:#5DB5F1:#FFFFFF>Text</gradient>` | none |
| Bold | `<bold>Text` | `&lText` |
| Reset | `<reset>` | `&r` |

The shipped files use MiniMessage. Texts already edited with `&` codes keep working and do not need to change.

A legacy colour code still ends a legacy `&l` bold before it, as it always did. Write `&a&lText` for green bold text, not `&l&aText`.

Gradients become one colour per letter.

## Click and Hover

`<click>`, `<hover>` and `<insert>` work in the `message` part of `messages.yml`, the chat line:

```yaml
command:
  reload:
    success:
      message: '<click:open_url:https://docs.smartspawner.site>Reloaded. <u>Open the docs</u></click>'
```

Everywhere else, in menus, items, titles, action bars and holograms, these tags are shown as typed and do nothing. On an item, a click could travel with the item into chat through other plugins, so it is never added.

Placeholders cannot be used inside a click or hover, and a placeholder value never becomes a click. A player who names an item `<click:run_command:/op me>` only ever shows that text. `<selector>`, `<score>` and `<nbt>` are not available anywhere.

See the [MiniMessage format](https://docs.advntr.dev/minimessage/format.html) for every tag.

## Placeholders

Placeholders are written `{name}`, for example `{amount}`. The MiniMessage form `<amount>` works too. The comment above a key lists the placeholders it receives.

A placeholder never adds colours of its own. A player or item name containing `<red>` shows the text as typed.

To show a placeholder-like word in angle brackets as plain text, for example in a usage line, put a backslash before it: `\<value>`.

## Small Caps

`<smallcaps>Open Storage</smallcaps>` shows as `ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ`. Placeholders inside it are converted too. Scripts without small capitals, such as Chinese, are left as they are.

`{entity_small}` is the mob name in small caps. The older name `{ᴇɴᴛɪᴛʏ}` still works.

## Each Player's Own Language

Set `language_auto_detect: true` in `config.yml` to show messages and menus in the language of each player's game client, when a folder for it exists. All built-in languages are then written to the plugin folder. A client language with no folder uses `language`. A Spanish client set to Mexico uses `es_ES` when there is no `es_MX`.

Spawner items, holograms, mob names and the console always use `language`, because everyone sees the same item or hologram.

A GUI layout's `texts.yml` is in one language. Add `texts_<language>.yml` next to it, for example `texts_vi_VN.yml`, with the texts for players in that language.

## Adding a Language

1. Create a folder in `language/`, for example `de_DE`.
2. Copy the files from `en_US/` into it and translate the texts. Keep the keys and placeholders unchanged.
3. Run `/ss language de_DE`.

A custom language is kept up to date when keys are renamed in a later version. New keys are not added to it, and show in English until translated, so compare it with `en_US` after updating.

## Upgrading From 1.8.3 or Older

Language folders in the old layout, with eight files each, are converted on the first start, custom ones too. Every edited text is kept under its new name, and the old files are saved in `language.backup-<date>/`. Keys the plugin does not know are kept and listed in the console.
