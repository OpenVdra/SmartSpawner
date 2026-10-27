package github.nighter.smartspawner.agentclient;

import github.nighter.smartspawner.agentclient.core.AgentDispatcher;
import github.nighter.smartspawner.agentclient.core.AgentException;
import github.nighter.smartspawner.agentclient.core.AgentReply;
import github.nighter.smartspawner.agentclient.core.AgentRequest;
import github.nighter.smartspawner.agentclient.mixin.ContainerScreenAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every command the client answers. All of them run on the client thread, from the client tick.
 *
 * <p>Besides moving, looking and clicking, three commands are for the screens a Paper plugin opens,
 * whose controls have no fixed place: {@code client.widgets} lists what a
 * screen holds, {@code client.press} clicks one by its label and {@code client.type} fills a text box.
 * A dialog lays its buttons out from the text it shows, so a script that clicked at coordinates would
 * break on the next wording change; a label survives it.
 */
final class ClientCommands {
    /** Ticks between two looks at a capture that has not finished, and how many looks to take. */
    private static final int CAPTURE_POLL_TICKS = 2;
    private static final int CAPTURE_ATTEMPTS = 30;
    /** Ticks a slot click is given to reach the server and its answer to come back. */
    private static final int SLOT_TICKS = 5;
    /** Ticks a respawn is given: a round trip to the server, then the screen closing on the next pass. */
    private static final int RESPAWN_TICKS = 10;

    private ClientCommands() { }

    static void register(AgentDispatcher dispatcher) {
        /* The heartbeat: what this is, and what it answers. Needs no world. */
        dispatcher.register("probe", (request, reply) -> {
            JsonObject result = new JsonObject();
            result.addProperty("queue", dispatcher.queue().directory().toAbsolutePath().toString());
            result.addProperty("pending", dispatcher.pending());
            JsonArray commands = new JsonArray();
            dispatcher.commands().forEach(commands::add);
            result.add("commands", commands);
            reply.ok(result);
        });

        /* Answers N ticks from now: "let the game catch up" without guessing at wall-clock sleeps. */
        dispatcher.register("wait", (request, reply) -> {
            int ticks = request.integer("ticks", 1, 20 * 60);
            dispatcher.defer(ticks, reply, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("ticks", ticks);
                reply.ok(result);
            });
        });

        /* Closes the game. The answer is written first, so out.jsonl is complete afterwards. */
        dispatcher.register("stop", (request, reply) -> {
            reply.ok();
            AgentClient.LOGGER.info("[Agent] stopping, as the queue asked");
            AgentClient.stop(client());
        });

        dispatcher.register("client.info", (request, reply) -> {
            Minecraft minecraft = client();
            JsonObject result = new JsonObject();
            result.addProperty("frameWidth", minecraft.getWindow().getWidth());
            result.addProperty("frameHeight", minecraft.getWindow().getHeight());
            result.addProperty("guiWidth", minecraft.getWindow().getGuiScaledWidth());
            result.addProperty("guiHeight", minecraft.getWindow().getGuiScaledHeight());
            result.addProperty("guiScale", minecraft.getWindow().getGuiScale());
            result.addProperty("screen", screenName(minecraft));
            result.addProperty("inWorld", minecraft.level != null);
            ServerData current = minecraft.getCurrentServer();
            result.addProperty("server", current == null ? null : current.ip);
            result.addProperty("player", minecraft.player == null ? null : minecraft.player.getScoreboardName());
            result.addProperty("language", minecraft.getLanguageManager().getSelected());
            result.addProperty("fps", minecraft.getFps());
            JsonArray keys = new JsonArray();
            Keys.names(minecraft).forEach(keys::add);
            result.add("keys", keys);
            // Whether sneak and sprint are held or toggled is the player's own setting, and it changes
            // what holding those keys means.
            result.addProperty("toggleCrouch", minecraft.options.toggleCrouch().get());
            result.addProperty("toggleSprint", minecraft.options.toggleSprint().get());
            result.addProperty("driven", ClientWindow.driven());
            result.addProperty("mouseGrabbed", minecraft.mouseHandler.isMouseGrabbed());
            reply.ok(result);
        });

        dispatcher.register("client.state", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            JsonObject result = new JsonObject();
            result.addProperty("name", player.getScoreboardName());
            result.addProperty("uuid", player.getUUID().toString());
            result.addProperty("alive", player.isAlive());
            result.addProperty("creative", player.isCreative());
            result.addProperty("dimension", player.level().dimension().identifier().toString());
            result.addProperty("x", round(player.getX()));
            result.addProperty("y", round(player.getY()));
            result.addProperty("z", round(player.getZ()));
            result.addProperty("yaw", round(player.getYRot()));
            result.addProperty("pitch", round(player.getXRot()));
            result.addProperty("hudHidden", minecraft.gui.hud.isHidden());
            result.addProperty("screen", screenName(minecraft));
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                result.addProperty("lookingAt", BuiltInRegistries.BLOCK.getKey(
                        minecraft.level.getBlockState(block.getBlockPos()).getBlock()).toString());
            }
            reply.ok(result);
        });

        /* Commands go through the player's own connection, so the server sees them exactly as typed. */
        dispatcher.register("client.command", (request, reply) -> {
            String command = request.string("command").strip();
            if (command.startsWith("/")) command = command.substring(1);
            player(client()).connection.sendCommand(command);
            JsonObject result = new JsonObject();
            result.addProperty("command", command);
            reply.ok(result);
        });

        dispatcher.register("client.chat", (request, reply) -> {
            player(client()).connection.sendChat(request.string("message"));
            reply.ok();
        });

        /*
         * The completions chat would offer for `text` (a command line without the slash), the server's
         * custom suggestions included. `usage` is the grey argument hint chat shows when there are none.
         */
        dispatcher.register("client.suggest", (request, reply) -> {
            var connection = client().getConnection();
            if (connection == null) throw new AgentException("client.suggest: not connected");
            String text = request.string("text");
            var commands = connection.getCommands();
            var parse = commands.parse(text, connection.getSuggestionsProvider());
            var usage = commands.getSmartUsage(parse.getContext().findSuggestionContext(text.length()).parent,
                    connection.getSuggestionsProvider());
            var future = commands.getCompletionSuggestions(parse);
            // The server answers custom suggestions over the network; give it a few ticks.
            dispatcher.defer(10, reply, () -> {
                JsonObject result = new JsonObject();
                JsonArray list = new JsonArray();
                if (future.isDone()) future.join().getList().forEach(suggestion -> list.add(suggestion.getText()));
                result.add("suggestions", list);
                JsonArray hints = new JsonArray();
                usage.values().forEach(hints::add);
                result.add("usage", hints);
                reply.ok(result);
            });
        });

        /* Writes the framebuffer to <queue>/screenshots/<name>.png at full resolution. */
        dispatcher.register("client.capture", (request, reply) -> {
            Minecraft minecraft = client();
            Frames.Capture capture = Frames.capture(minecraft, dispatcher.queue().directory(),
                    request.string("name", null));
            waitForCapture(dispatcher, reply, capture, CAPTURE_ATTEMPTS, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("file", capture.file().toAbsolutePath().toString());
                result.addProperty("frameWidth", minecraft.getWindow().getWidth());
                result.addProperty("frameHeight", minecraft.getWindow().getHeight());
                result.addProperty("guiScale", minecraft.getWindow().getGuiScale());
                result.addProperty("message", capture.message().get());
                reply.ok(result);
            });
        });

        /* Holds keys for a number of ticks, then lets go. */
        dispatcher.register("client.hold", (request, reply) -> {
            Minecraft minecraft = client();
            List<String> names = request.strings("keys");
            if (names.isEmpty()) throw new AgentException("client.hold: 'keys' needs at least one key");
            int ticks = request.integer("ticks", 1, 20 * 60);
            Map<String, KeyMapping> held = new LinkedHashMap<>();
            for (String name : names) held.put(name, Keys.of(minecraft, name));
            held.values().forEach(key -> key.setDown(true));
            dispatcher.defer(ticks, reply, () -> {
                held.values().forEach(key -> key.setDown(false));
                reply.ok();
            });
        });

        /* Sets one key's state and leaves it there, for a hold that spans several requests. */
        dispatcher.register("client.key", (request, reply) -> {
            KeyMapping key = Keys.of(client(), request.string("key"));
            key.setDown(request.flag("down", true));
            JsonObject result = new JsonObject();
            result.addProperty("down", key.isDown());
            reply.ok(result);
        });

        /* Turns the player's head, for a shot of the world or to face a block before client.use. */
        dispatcher.register("client.look", (request, reply) -> {
            LocalPlayer player = player(client());
            player.setYRot(request.decimal("yaw", player.getYRot()));
            player.setXRot(request.decimal("pitch", player.getXRot()));
            reply.ok();
        });

        /*
         * Right-clicks the block the player is looking at, as the use key would. With sneak held first
         * (client.key sneak) this is a shift-right-click.
         */
        dispatcher.register("client.use", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            if (!(minecraft.hitResult instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) {
                throw new AgentException("client.use: the player is not looking at a block");
            }
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, block);
            player.swing(InteractionHand.MAIN_HAND);
            dispatcher.defer(SLOT_TICKS, reply, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("screen", screenName(minecraft));
                reply.ok(result);
            });
        });

        dispatcher.register("client.screen", (request, reply) -> {
            Minecraft minecraft = client();
            String open = request.choice("open", "none", "none", "inventory");
            LocalPlayer player = minecraft.player;
            if (open.equals("inventory")) {
                minecraft.gui.setScreen(new InventoryScreen(player(minecraft)));
            } else if (player != null && player.containerMenu != player.inventoryMenu) {
                // A container the server opened has to be closed the way Escape closes it, with a packet:
                // gui.setScreen(null) alone leaves the server believing it is still open, and the plugin
                // then treats the next chest it opens as part of the old session.
                player.closeContainer();
            } else {
                minecraft.gui.setScreen(null);
            }
            JsonObject result = new JsonObject();
            result.addProperty("screen", screenName(minecraft));
            reply.ok(result);
        });

        /*
         * Sets the GUI scale, 0 for auto. A maximised window picks the largest scale that fits, and a
         * wide dialog (the icon picker's four columns) overflows it; one step down fits it whole.
         */
        dispatcher.register("client.guiScale", (request, reply) -> {
            Minecraft minecraft = client();
            minecraft.options.guiScale().set(request.integer("scale", 0, 8));
            minecraft.resizeGui();
            JsonObject result = new JsonObject();
            result.addProperty("guiScale", minecraft.getWindow().getGuiScale());
            result.addProperty("guiWidth", minecraft.getWindow().getGuiScaledWidth());
            result.addProperty("guiHeight", minecraft.getWindow().getGuiScaledHeight());
            reply.ok(result);
        });

        /* F1: hides the hotbar and the hand for a clean shot of the world. */
        dispatcher.register("client.hud.toggle", (request, reply) -> {
            Minecraft minecraft = client();
            // 26.2 moved the flag into Hud, which only exposes a toggle.
            boolean hidden = request.has("hidden") ? request.flag("hidden", false) : !minecraft.gui.hud.isHidden();
            if (hidden != minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
            JsonObject result = new JsonObject();
            result.addProperty("hidden", minecraft.gui.hud.isHidden());
            reply.ok(result);
        });

        /*
         * Clears what would otherwise sit in every shot: the chat history (commands a script sent
         * answer in it), the tutorial hints and any toast still on screen. Run it right before a
         * capture: chat is cleared rather than hidden, because a server refuses commands from a
         * client whose chat setting is "Hidden".
         */
        dispatcher.register("client.clean", (request, reply) -> {
            Minecraft minecraft = client();
            minecraft.gui.hud.getChat().clearMessages(false);
            minecraft.gui.toastManager().clear();
            minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            reply.ok();
        });

        /*
         * Every widget on the open screen, nested ones included, with its label and its rectangle in GUI
         * pixels. What a script reads before it presses something it has not pressed before.
         */
        dispatcher.register("client.widgets", (request, reply) -> {
            Minecraft minecraft = client();
            Screen screen = screen(minecraft, "client.widgets");
            JsonArray widgets = new JsonArray();
            int index = 0;
            for (AbstractWidget widget : widgets(screen)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("index", index++);
                entry.addProperty("type", widget.getClass().getSimpleName());
                entry.addProperty("label", widget.getMessage().getString());
                entry.addProperty("x", widget.getX());
                entry.addProperty("y", widget.getY());
                entry.addProperty("width", widget.getWidth());
                entry.addProperty("height", widget.getHeight());
                entry.addProperty("active", widget.active);
                if (widget instanceof Checkbox box) entry.addProperty("checked", box.selected());
                if (widget instanceof EditBox box) entry.addProperty("value", box.getValue());
                if (widget instanceof MultiLineEditBox box) entry.addProperty("value", box.getValue());
                widgets.add(entry);
            }
            JsonObject result = new JsonObject();
            result.addProperty("screen", screen.getClass().getName());
            result.addProperty("title", screen.getTitle().getString());
            result.addProperty("width", screen.width);
            result.addProperty("height", screen.height);
            result.add("widgets", widgets);
            reply.ok(result);
        });

        /*
         * Clicks the widget whose label contains `text` (case ignored), the `nth` one when several do,
         * or the one at `index` in client.widgets' list. Answered a few ticks later with the screen then
         * open, since a dialog button's action is a round trip to the server.
         */
        dispatcher.register("client.press", (request, reply) -> {
            Minecraft minecraft = client();
            Screen screen = screen(minecraft, "client.press");
            AbstractWidget widget = find(screen, request);
            // `checked` makes a checkbox press idempotent: a dialog may remember its last state, so
            // pressing blindly would turn off what the script means to turn on.
            if (widget instanceof Checkbox box && request.has("checked")
                    && box.selected() == request.flag("checked", true)) {
                JsonObject result = new JsonObject();
                result.addProperty("pressed", widget.getMessage().getString());
                result.addProperty("checked", box.selected());
                result.addProperty("skipped", true);
                reply.ok(result);
                return;
            }
            double x = widget.getX() + widget.getWidth() / 2.0;
            double y = widget.getY() + widget.getHeight() / 2.0;
            int button = request.has("button") ? request.integer("button", 0, 2) : 0;
            if (Pointer.shown()) Pointer.place(minecraft, x, y);
            boolean taken = click(screen, x, y, button);
            String label = widget.getMessage().getString();
            dispatcher.defer(request.integer("ticks", SLOT_TICKS), reply, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("pressed", label);
                result.addProperty("taken", taken);
                result.addProperty("screen", screenName(minecraft));
                Screen now = minecraft.gui.screen();
                result.addProperty("title", now == null ? null : now.getTitle().getString());
                reply.ok(result);
            });
        });

        /* Fills a text box: the `nth` one on the screen, or the one whose label contains `label`. */
        dispatcher.register("client.type", (request, reply) -> {
            Minecraft minecraft = client();
            Screen screen = screen(minecraft, "client.type");
            String text = request.string("text");
            List<AbstractWidget> boxes = new ArrayList<>();
            for (AbstractWidget widget : widgets(screen)) {
                if (!(widget instanceof EditBox) && !(widget instanceof MultiLineEditBox)) continue;
                if (request.has("label") && !matches(widget, request.string("label"))) continue;
                boxes.add(widget);
            }
            int nth = request.integer("nth", 0);
            if (nth >= boxes.size()) {
                throw new AgentException("client.type: the screen has " + boxes.size() + " matching text boxes");
            }
            AbstractWidget box = boxes.get(nth);
            screen.setFocused(box);
            if (box instanceof EditBox edit) edit.setValue(text);
            else ((MultiLineEditBox) box).setValue(text);
            reply.ok();
        });

        /*
         * A mouse click on the open screen at a point in GUI pixels, from the centre by default (a
         * container screen is centred, so its controls keep their offset from it at any window size),
         * or from the corner, top or bottom.
         */
        dispatcher.register("client.click", (request, reply) -> {
            Minecraft minecraft = client();
            Screen screen = screen(minecraft, "client.click");
            double[] point = point(request, minecraft);
            int button = request.has("button") ? request.integer("button", 0, 2) : 0;
            String target = screen.getChildAt(point[0], point[1]).map(child -> child.getClass().getName()).orElse(null);
            if (Pointer.shown()) Pointer.place(minecraft, point[0], point[1]);
            boolean taken = click(screen, point[0], point[1], button);
            JsonObject result = new JsonObject();
            result.addProperty("x", point[0]);
            result.addProperty("y", point[1]);
            result.addProperty("target", target);
            result.addProperty("taken", taken);
            reply.ok(result);
        });

        /* Turns the mouse wheel over the open screen; `amount` is notches, positive scrolling down. */
        dispatcher.register("client.scroll", (request, reply) -> {
            Minecraft minecraft = client();
            Screen screen = screen(minecraft, "client.scroll");
            double[] point = point(request, minecraft);
            if (Pointer.shown()) Pointer.place(minecraft, point[0], point[1]);
            int amount = request.integer("amount", -100, 100);
            boolean taken = false;
            // One event a notch, as a real wheel sends them. The game's wheel is positive upwards.
            for (int notch = 0; notch < Math.abs(amount); notch++) {
                taken |= screen.mouseScrolled(point[0], point[1], 0.0, -Math.signum(amount));
            }
            JsonObject result = new JsonObject();
            result.addProperty("taken", taken);
            reply.ok(result);
        });

        /*
         * Shows the virtual pointer at a point, placed as client.click places one, so the screen draws
         * its hover and tooltip there. `hide` hands the position back to the real mouse.
         */
        dispatcher.register("client.mouse", (request, reply) -> {
            Minecraft minecraft = client();
            if (request.flag("hide", false)) {
                Pointer.hide();
                reply.ok();
                return;
            }
            double[] point = point(request, minecraft);
            Pointer.place(minecraft, point[0], point[1]);
            JsonObject result = new JsonObject();
            result.addProperty("x", point[0]);
            result.addProperty("y", point[1]);
            reply.ok(result);
        });

        /* Puts the virtual pointer over one slot of the open container, for its tooltip. */
        dispatcher.register("client.hover", (request, reply) -> {
            Minecraft minecraft = client();
            if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> container)) {
                throw new AgentException("client.hover: no container screen is open");
            }
            LocalPlayer player = player(minecraft);
            int index = request.has("inventory") ? menuSlot(player, request.integer("inventory"))
                    : request.integer("slot", 0, player.containerMenu.slots.size() - 1);
            Slot slot = player.containerMenu.slots.get(index);
            ContainerScreenAccessor panel = (ContainerScreenAccessor) container;
            // A slot's position is its 16x16 item's corner, relative to the panel.
            Pointer.place(minecraft, panel.agent$leftPos() + slot.x + 8, panel.agent$topPos() + slot.y + 8);
            JsonObject result = new JsonObject();
            result.addProperty("slot", index);
            result.add("item", item(slot.getItem()));
            reply.ok(result);
        });

        /* The open container's slots that hold something, by the index client.slot takes. */
        /*
         * An item's tooltip as text with its styles: the name and lore a plugin set, read as values
         * instead of off a capture. `slot` (or `inventory`) is a slot of the open menu, as client.slots
         * numbers them; `hand` is mainhand or offhand. Each line has `text` and its `segments`.
         */
        dispatcher.register("client.tooltip", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            ItemStack stack;
            if (request.has("hand")) {
                String hand = request.choice("hand", "mainhand", "mainhand", "offhand");
                stack = player.getItemInHand(hand.equals("mainhand") ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            } else {
                int slot = request.has("inventory") ? menuSlot(player, request.integer("inventory"))
                        : request.integer("slot", 0, player.containerMenu.slots.size() - 1);
                stack = player.containerMenu.slots.get(slot).getItem();
            }
            if (stack.isEmpty()) throw new AgentException("client.tooltip: that slot is empty");
            TooltipFlag flag = request.flag("advanced", false) ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
            JsonArray lines = new JsonArray();
            for (Component line : stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, flag)) {
                lines.add(Styled.json(line));
            }
            JsonObject result = item(stack);
            result.add("lines", lines);
            reply.ok(result);
        });

        /*
         * What the server told this player: chat lines, action bars, titles and subtitles, with their
         * styles, logged as they arrived (see Messages). `since` is the `last` of an earlier answer, so a
         * script reads only what its action caused; `kinds` narrows it; `clear` empties the log first.
         */
        dispatcher.register("client.messages", (request, reply) -> {
            if (request.flag("clear", false)) Messages.clear();
            java.util.Set<String> kinds = new java.util.HashSet<>(request.strings("kinds"));
            for (String kind : kinds) {
                if (!Messages.KINDS.contains(kind)) {
                    throw new AgentException("client.messages: unknown kind '" + kind + "'; the kinds are " + Messages.KINDS);
                }
            }
            long since = request.integer("since", 0);
            reply.ok(Messages.since(since, kinds));
        });

        /*
         * The colour of the framebuffer at named points, in GUI pixels by default (`space`: frame for
         * physical ones). Samples the last capture unless `capture` is true or there is none yet.
         */
        dispatcher.register("client.pixels", (request, reply) -> {
            Minecraft minecraft = client();
            List<int[]> points = points(request);
            boolean gui = request.choice("space", "gui", "gui", "frame").equals("gui");
            boolean fresh = request.flag("capture", Frames.lastFile() == null);
            if (!fresh) {
                reply.ok(samples(minecraft, Frames.lastFile(), points, gui));
                return;
            }
            Frames.Capture capture = Frames.capture(minecraft, dispatcher.queue().directory(),
                    request.string("name", null));
            waitForCapture(dispatcher, reply, capture, CAPTURE_ATTEMPTS,
                    () -> reply.ok(samples(minecraft, capture.file(), points, gui)));
        });

        /* Selects a hotbar slot, 0 to 8, so client.use acts with that item. The game sends it on its own tick. */
        dispatcher.register("client.hotbar", (request, reply) -> {
            LocalPlayer player = player(client());
            player.getInventory().setSelectedSlot(request.integer("slot", 0, 8));
            dispatcher.defer(SLOT_TICKS, reply, () -> {
                JsonObject result = item(player.getInventory().getSelectedItem());
                result.addProperty("slot", player.getInventory().getSelectedSlot());
                reply.ok(result);
            });
        });

        /* Presses the death screen's respawn the way the screen does, through the player's connection. */
        dispatcher.register("client.respawn", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            if (player.isAlive()) {
                throw new AgentException("client.respawn: " + player.getScoreboardName() + " is alive; kill them first");
            }
            player.respawn();
            minecraft.gui.setScreen(null);
            dispatcher.defer(RESPAWN_TICKS, reply, () -> {
                JsonObject result = new JsonObject();
                result.addProperty("screen", screenName(minecraft));
                result.addProperty("alive", minecraft.player != null && minecraft.player.isAlive());
                reply.ok(result);
            });
        });

        dispatcher.register("client.slots", (request, reply) -> reply.ok(slots(player(client()))));

        /*
         * A click on one slot of the open container, sent to the server as a screen sends it. The slot
         * is the menu's index, or with `inventory` the player's own inventory index wherever this menu
         * put it. Answered a few ticks later, once the server has answered.
         */
        dispatcher.register("client.slot", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            int slot = request.has("inventory") ? menuSlot(player, request.integer("inventory"))
                    : request.integer("slot", 0, player.containerMenu.slots.size() - 1);
            int button = request.has("button") ? request.integer("button", 0, 8) : 0;
            String action = request.choice("action", "pickup",
                    "pickup", "quick_move", "swap", "clone", "throw", "quick_craft", "pickup_all");
            minecraft.gameMode.handleContainerInput(player.containerMenu.containerId, slot, button,
                    ContainerInput.valueOf(action.toUpperCase(Locale.ROOT)), player);
            dispatcher.defer(SLOT_TICKS, reply, () -> reply.ok(slots(player)));
        });

        /*
         * Sends the creative "set slot" packet for one hotbar slot with whatever the client holds there,
         * as the creative inventory screen does whenever a player touches that slot. `hotbar` is 0-8.
         */
        dispatcher.register("client.creativeSync", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            int hotbar = request.integer("hotbar", 0, 8);
            ItemStack stack = player.getInventory().getItem(hotbar).copy();
            minecraft.gameMode.handleCreativeModeItemAdd(stack, 36 + hotbar);
            JsonObject result = new JsonObject();
            result.addProperty("sent", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            result.addProperty("count", stack.getCount());
            dispatcher.defer(SLOT_TICKS, reply, () -> reply.ok(result));
        });

        /*
         * Shift-clicks every filled slot of the player's own inventory into the open container, in one
         * request: the quick way to fill a chest for a shot.
         */
        dispatcher.register("client.fill", (request, reply) -> {
            Minecraft minecraft = client();
            LocalPlayer player = player(minecraft);
            int moved = 0;
            for (int index = 0; index < player.containerMenu.slots.size(); index++) {
                Slot slot = player.containerMenu.slots.get(index);
                if (slot.container != player.getInventory() || !slot.hasItem()) continue;
                minecraft.gameMode.handleContainerInput(player.containerMenu.containerId, index, 0,
                        ContainerInput.QUICK_MOVE, player);
                moved++;
            }
            int count = moved;
            dispatcher.defer(SLOT_TICKS, reply, () -> {
                JsonObject result = slots(player);
                result.addProperty("moved", count);
                reply.ok(result);
            });
        });

        /*
         * Switches the client's language without writing options.txt. Only the translations are reloaded,
         * which is all a vanilla client needs: the fonts already hold every script.
         */
        dispatcher.register("client.language", (request, reply) -> {
            Minecraft minecraft = client();
            String code = request.string("code");
            minecraft.getLanguageManager().setSelected(code);
            minecraft.options.languageCode = code;
            minecraft.getLanguageManager().onResourceManagerReload(minecraft.getResourceManager());
            JsonObject result = new JsonObject();
            result.addProperty("language", minecraft.getLanguageManager().getSelected());
            reply.ok(result);
        });

        dispatcher.register("client.disconnect", (request, reply) -> {
            Minecraft minecraft = client();
            if (minecraft.level == null) throw new AgentException("client.disconnect: no world is loaded");
            reply.ok();
            minecraft.disconnect(new TitleScreen(), false);
        });

        dispatcher.register("client.connect", (request, reply) -> {
            Minecraft minecraft = client();
            String address = request.string("address", "localhost:25565");
            if (minecraft.level != null) {
                throw new AgentException("client.connect: a world is already loaded; disconnect first");
            }
            reply.ok();
            ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("agent", address, ServerData.Type.OTHER), false, null);
        });
    }

    private static void waitForCapture(AgentDispatcher dispatcher, AgentReply reply, Frames.Capture capture,
                                       int attemptsLeft, Runnable ready) {
        dispatcher.defer(CAPTURE_POLL_TICKS, reply, () -> {
            String failure = capture.failure().get();
            if (failure != null) {
                reply.fail("the capture failed: " + failure);
                return;
            }
            if (capture.finished() && Files.isRegularFile(capture.file())) {
                ready.run();
                return;
            }
            if (attemptsLeft <= 1) {
                reply.fail("the capture did not reach " + capture.file() + " within "
                        + CAPTURE_ATTEMPTS * CAPTURE_POLL_TICKS + " ticks");
                return;
            }
            waitForCapture(dispatcher, reply, capture, attemptsLeft - 1, ready);
        });
    }

    /** Every widget on the screen, depth first, including the ones inside scroll areas and layouts. */
    private static List<AbstractWidget> widgets(Screen screen) {
        List<AbstractWidget> found = new ArrayList<>();
        collect(screen.children(), found);
        return found;
    }

    private static void collect(List<? extends GuiEventListener> children, List<AbstractWidget> found) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) {
                if (!widget.visible) continue;
                found.add(widget);
            }
            if (child instanceof ContainerEventHandler container) collect(container.children(), found);
        }
    }

    private static AbstractWidget find(Screen screen, AgentRequest request) {
        List<AbstractWidget> all = widgets(screen);
        if (request.has("index")) {
            int index = request.integer("index");
            if (index < 0 || index >= all.size()) {
                throw new AgentException("client.press: no widget " + index + "; the screen has " + all.size());
            }
            return all.get(index);
        }
        String text = request.string("text");
        boolean exact = request.flag("exact", false);
        List<AbstractWidget> matching = new ArrayList<>();
        for (AbstractWidget widget : all) {
            // Buttons only: a dialog's body text and a text box's label often repeat a button's word
            // ("press Search to filter"), and pressing those does nothing.
            if (!(widget instanceof AbstractButton)) continue;
            if (exact ? widget.getMessage().getString().equalsIgnoreCase(text) : matches(widget, text)) {
                matching.add(widget);
            }
        }
        int nth = request.integer("nth", 0);
        if (nth >= matching.size()) {
            List<String> labels = new ArrayList<>();
            for (AbstractWidget widget : all) labels.add(widget.getMessage().getString());
            throw new AgentException("client.press: " + matching.size() + " widgets match '" + text
                    + "'; the labels on screen are " + labels);
        }
        return matching.get(nth);
    }

    private static boolean matches(AbstractWidget widget, String text) {
        return widget.getMessage().getString().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    /** A press and a release at one point, the two calls the mouse handler makes. */
    private static boolean click(Screen screen, double x, double y, int button) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
        boolean taken = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return taken;
    }

    /**
     * The point a request names, in GUI pixels: `x` and `y` measured `from` the centre (the default),
     * the corner, or the top or bottom edge with x still from the centre. Without either, where the
     * pointer is, when it is shown.
     */
    private static double[] point(AgentRequest request, Minecraft minecraft) {
        if (!request.has("x") && !request.has("y") && Pointer.shown()) {
            return new double[] {Pointer.x(), Pointer.y()};
        }
        Screen screen = minecraft.gui.screen();
        double width = screen != null ? screen.width : minecraft.getWindow().getGuiScaledWidth();
        double height = screen != null ? screen.height : minecraft.getWindow().getGuiScaledHeight();
        String from = request.choice("from", "centre", "centre", "corner", "top", "bottom");
        double x = request.decimal("x", 0.0F) + (from.equals("corner") ? 0.0 : width / 2.0);
        double y = request.decimal("y", 0.0F) + switch (from) {
            case "centre" -> height / 2.0;
            case "bottom" -> height;
            default -> 0.0;
        };
        return new double[] {x, y};
    }

    private static JsonObject slots(LocalPlayer player) {
        JsonArray filled = new JsonArray();
        for (int index = 0; index < player.containerMenu.slots.size(); index++) {
            Slot slot = player.containerMenu.slots.get(index);
            if (!slot.hasItem()) continue;
            JsonObject entry = item(slot.getItem());
            entry.addProperty("slot", index);
            if (slot.container == player.getInventory()) entry.addProperty("inventory", slot.getContainerSlot());
            filled.add(entry);
        }
        JsonObject result = new JsonObject();
        result.addProperty("menu", player.containerMenu.getClass().getName());
        result.addProperty("size", player.containerMenu.slots.size());
        result.add("carried", item(player.containerMenu.getCarried()));
        result.add("slots", filled);
        return result;
    }

    private static JsonObject samples(Minecraft minecraft, Path file, List<int[]> points, boolean gui) {
        BufferedImage image = Frames.image(file);
        double scale = minecraft.getWindow().getGuiScale();
        JsonArray samples = new JsonArray();
        for (int[] point : points) {
            int frameX = gui ? (int) Math.round((point[0] + 0.5) * scale) : point[0];
            int frameY = gui ? (int) Math.round((point[1] + 0.5) * scale) : point[1];
            JsonObject sample = new JsonObject();
            sample.addProperty("x", point[0]);
            sample.addProperty("y", point[1]);
            sample.addProperty("frameX", frameX);
            sample.addProperty("frameY", frameY);
            if (frameX < 0 || frameY < 0 || frameX >= image.getWidth() || frameY >= image.getHeight()) {
                sample.addProperty("error", "outside the " + image.getWidth() + "x" + image.getHeight() + " frame");
            } else {
                int argb = image.getRGB(frameX, frameY);
                sample.addProperty("argb", Frames.hex(argb));
                sample.addProperty("red", (argb >> 16) & 0xFF);
                sample.addProperty("green", (argb >> 8) & 0xFF);
                sample.addProperty("blue", argb & 0xFF);
            }
            samples.add(sample);
        }
        JsonObject result = new JsonObject();
        result.addProperty("file", file.toAbsolutePath().toString());
        result.addProperty("width", image.getWidth());
        result.addProperty("height", image.getHeight());
        result.addProperty("guiScale", scale);
        result.addProperty("space", gui ? "gui" : "frame");
        result.add("points", samples);
        return result;
    }

    /** The points to sample, written as {@code [x, y]} or as <code>{"x": .., "y": ..}</code>. */
    private static List<int[]> points(AgentRequest request) {
        List<int[]> points = new ArrayList<>();
        for (JsonElement element : request.list("points")) {
            if (element.isJsonArray() && element.getAsJsonArray().size() == 2) {
                JsonArray pair = element.getAsJsonArray();
                points.add(new int[] {pair.get(0).getAsInt(), pair.get(1).getAsInt()});
            } else if (element.isJsonObject() && element.getAsJsonObject().has("x")) {
                JsonObject object = element.getAsJsonObject();
                points.add(new int[] {object.get("x").getAsInt(), object.get("y").getAsInt()});
            } else {
                throw new AgentException("client.pixels: a point is [x, y] or {\"x\": .., \"y\": ..}, got " + element);
            }
        }
        if (points.isEmpty()) throw new AgentException("client.pixels: 'points' needs at least one point");
        return points;
    }

    private static int menuSlot(LocalPlayer player, int inventorySlot) {
        for (int index = 0; index < player.containerMenu.slots.size(); index++) {
            Slot slot = player.containerMenu.slots.get(index);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) return index;
        }
        throw new AgentException("the open menu has no slot for inventory slot " + inventorySlot);
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        entry.addProperty("count", stack.getCount());
        return entry;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String screenName(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        return screen == null ? null : screen.getClass().getName();
    }

    private static Screen screen(Minecraft minecraft, String command) {
        Screen screen = minecraft.gui.screen();
        if (screen == null) throw new AgentException(command + ": no screen is open");
        return screen;
    }

    private static Minecraft client() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new AgentException("there is no client in this process");
        return minecraft;
    }

    private static LocalPlayer player(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) throw new AgentException("no player on this client; it is at " + screenName(minecraft));
        return player;
    }
}
