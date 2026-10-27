# Agent client

A Fabric client mod for Minecraft 26.2 that an agent drives through a file queue. It joins the
plugin's dev server (`./gradlew runServer`) and takes screenshots: no mouse, no focus, the desktop
stays usable.

The Minecraft version here must match `mcRun { minecraftVersion }` in `core/build.gradle.kts`.
26.1+ ships unobfuscated, so this uses the plain `fabric-loom` with no mappings.

## Run

```bash
./gradlew runServer                         # repository root, the Paper dev server
./gradlew -p tools/agent-client runClient   # joins localhost:25565 as the offline player "Dev"
```

Options: `-Pusername=Steve -PagentName=steve` for a second player with its own queue,
`-Pserver=host:port` (or `none`), `-Pagent=<file>` to answer a script and close.
`Dev` must be op on the dev server for `/ss give` and the like: `op Dev` from the server console, or
add it to `run/ops.json` (offline UUID `380df991-f603-344c-a090-369bad2a924a`) before boot.

## Drive

The queue is `run/agent/<name>/`: append JSON lines to `in.jsonl`, read `out.jsonl`. `drive.py`
does the matching and waiting:

```bash
python tools/agent-client/drive.py tools/agent-client/run/agent/client requests.jsonl
echo '{"command":"client.info"}' | python tools/agent-client/drive.py tools/agent-client/run/agent/client -
```

`--ready 400` waits for a client started after the command to open its queue. Captures land in
`run/agent/client/screenshots/<name>.png` at full framebuffer size. `ready.json` lists every command.
The ones for a plugin's screens:

| Command | Does |
|---|---|
| `client.command` | Runs a command as the player, e.g. `{"command":"client.command","args":{"command":"ss give Dev zombie 1"}}` |
| `client.use` / `client.look` | Right-clicks what the crosshair targets (opens a spawner's GUI) / sets `yaw` and `pitch` |
| `client.slots` / `client.slot` | Reads the open container's slots (id and count) / clicks one (`action`: `pickup`, `quick_move`, ...) |
| `client.tooltip` | An item's name and lore as text with styles: `slot` or `inventory` of the open menu, or `hand` (`mainhand`, `offhand`). Each line has `text` and `segments` (`color`, and `bold`/`italic`/... when on) |
| `client.messages` | Chat lines, action bars, titles and subtitles the player received, with their styles. `since` (the `last` of an earlier answer), `kinds`, `clear` |
| `client.hotbar` | Selects hotbar slot `slot` (0 to 8), so `client.use` acts with that item |
| `client.pixels` | Framebuffer colours at `points` (`[x, y]`, GUI pixels unless `space` is `frame`), from the last capture or a new one (`capture`) |
| `client.respawn` | Presses respawn on the death screen |
| `client.widgets` | Every widget on the open screen: type, label, rectangle, checkbox state, text box value |
| `client.press` | Clicks the button whose label contains `text` (`exact`, `nth`, `index`; `checked` for an idempotent checkbox) |
| `client.type` | Fills a text box (`nth`, `label`) |
| `client.fill` | Shift-clicks the whole player inventory into the open container |
| `client.hover` | Puts the virtual pointer over a container slot, for its tooltip |
| `client.mouse` | Shows the virtual pointer at a point; `{"from":"corner","x":1,"y":1}` parks it out of the way before a shot |
| `client.clean` | Clears chat, toasts and tutorial hints; run it right before `client.capture` |
| `client.guiScale` | Sets the GUI scale |
| `client.screen` | `none` closes a server container the way Escape does (with the close packet) |
| `stop` | Closes the client |

Prefer `client.tooltip` and `client.messages` over a capture for checking text: they answer the
colours and decorations as values, and the action bar is still readable after it has faded.

Parameters go under `args`. Requests may also carry `expect` / `checks`; `drive.py` exits 1 when the reply does not match (see its
docstring).

## Gotchas

- A label match also hits body text. `client.press` only matches buttons, and `exact` settles the rest.
- A real mouse resting over the window hovers a button; park the virtual pointer first.
- mc-run forces `online-mode=false` on the dev server, so a launcher client joins with its offline UUID
  and loses any op granted to its online UUID.
