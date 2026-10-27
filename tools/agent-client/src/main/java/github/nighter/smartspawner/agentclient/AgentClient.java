package github.nighter.smartspawner.agentclient;

import github.nighter.smartspawner.agentclient.core.AgentDispatcher;
import github.nighter.smartspawner.agentclient.core.AgentQueue;
import github.nighter.smartspawner.agentclient.core.AgentWatchdog;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * The agent, wired up: one queue in {@code <gameDir>/agent/<name>/}, polled on the client tick.
 *
 * <p>{@code <name>} is {@code client} unless {@code -Dsmartspawner.agent=<name>} says otherwise,
 * which is how two of these clients run side by side without sharing a queue.
 */
public final class AgentClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SmartSpawnerAgent");

    private static final String NAME_PROPERTY = "smartspawner.agent";
    /** {@code -Dsmartspawner.agent.script=<file>} answers that file once at startup, then closes the game. */
    private static final String SCRIPT_PROPERTY = "smartspawner.agent.script";
    /** Seconds an unattended run may go without answering, or without ticking, before it gives up. */
    private static final int STALL_SECONDS = Integer.getInteger("smartspawner.agent.stallSeconds", 300);

    private static AgentDispatcher dispatcher;
    private static boolean started;
    private static long ticks;

    @Override
    public void onInitializeClient() {
        String name = System.getProperty(NAME_PROPERTY, "client").trim();
        Path directory = FabricLoader.getInstance().getGameDir().resolve("agent").resolve(name);
        JsonObject about = new JsonObject();
        about.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
        about.addProperty("side", "client");
        about.addProperty("name", name);
        dispatcher = new AgentDispatcher(new AgentQueue(directory, LOGGER), LOGGER, about);
        ClientCommands.register(dispatcher);
    }

    /** Called at the end of every client tick, from {@code MinecraftTickMixin}. */
    public static void tick(Minecraft minecraft) {
        if (dispatcher == null) return;
        if (!started) {
            // The first tick rather than the initializer: the window and the options exist by now.
            started = true;
            ClientWindow.open(minecraft);
            start(minecraft);
        }
        Pointer.tick(minecraft);
        dispatcher.tick();
        ClientWindow.showStatus(minecraft, dispatcher.status(), ++ticks);
    }

    private static void start(Minecraft minecraft) {
        boolean open = dispatcher.start();
        String script = System.getProperty(SCRIPT_PROPERTY);
        if (script == null || script.isBlank()) return;
        if (!open) {
            LOGGER.error("[Agent] DONE refused, the script was not run: the queue in {} is not this game's",
                    dispatcher.queue().directory().toAbsolutePath());
            minecraft.stop();
            return;
        }
        // Nobody is watching an unattended run, so it has to end on its own even when it cannot finish.
        dispatcher.watchStall(STALL_SECONDS, () -> {
            LOGGER.error("[Agent] DONE stalled: nothing answered for {} s while {} waited", STALL_SECONDS,
                    dispatcher.status());
            dispatcher.stop("the run stalled, and was stopped before this request was started");
            minecraft.stop();
        });
        AgentWatchdog.start(dispatcher, Thread.currentThread(), STALL_SECONDS, LOGGER);
        dispatcher.runScript(Path.of(script), () -> {
            LOGGER.info("[Agent] DONE script answered, out={}", dispatcher.queue().file(AgentQueue.OUT).toAbsolutePath());
            stop(minecraft);
        });
    }

    /** Answers whatever is still deferred and closes the queue, without stopping the game. */
    public static void flush() {
        if (dispatcher != null) dispatcher.stop();
    }

    /** Closes the queue, then the game. */
    static void stop(Minecraft minecraft) {
        flush();
        minecraft.stop();
    }
}
