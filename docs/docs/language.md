---
title: Language Files
---

# Language Files

Every message, menu text and item name comes from the files in `plugins/SmartSpawner/language/<language>/`. Pick the language with `language` in `config.yml`, or switch it live with `/ss language <name>`.

Only the active language is written to the plugin folder. Another built-in language is added the first time it is selected.

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

Click and hover tags are not supported yet. Gradients become one colour per letter.

See the [MiniMessage format](https://docs.advntr.dev/minimessage/format.html) for every tag.

## Placeholders

Placeholders are written `{name}`, for example `{amount}`. The MiniMessage form `<amount>` works too. The comment above a key lists the placeholders it receives.

A placeholder never adds colours of its own. A player or item name containing `<red>` shows the text as typed.

To show a placeholder-like word in angle brackets as plain text, for example in a usage line, put a backslash before it: `\<value>`.

## Small Caps

`<smallcaps>Open Storage</smallcaps>` shows as `ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ`. Placeholders inside it are converted too. Scripts without small capitals, such as Chinese, are left as they are.

`{entity_small}` is the mob name in small caps. The older name `{ᴇɴᴛɪᴛʏ}` still works.

## Adding a Language

1. Create a folder in `language/`, for example `de_DE`.
2. Copy the files from `en_US/` into it and translate the texts. Keep the keys and placeholders unchanged.
3. Run `/ss language de_DE`.

A custom language is kept up to date when keys are renamed in a later version. New keys are not added to it, so compare it with `en_US` after updating.
