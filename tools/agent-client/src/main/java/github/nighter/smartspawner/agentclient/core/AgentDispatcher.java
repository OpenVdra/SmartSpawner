package github.nighter.smartspawner.agentclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the lines an {@link AgentQueue} hands over into answers, one game tick at a time.
 *
 * <p>Everything runs on the game thread, in {@link #tick}: a probe that read the player, the HUD or
 * the framebuffer from another thread would read a half-updated one. Polling is a file size check
 * every {@link #POLL_TICKS} ticks, which is cheap enough to leave on for every development run.
 *
 * <p>Requests are answered <em>in order, one at a time</em>. A handler that takes a deferral - holding
 * a key for thirty ticks, waiting for a capture to reach disk - stops the next request being started
 * until it has answered. Without that a script is a batch rather than a sequence: every line of it
 * runs in the tick the poll read it, so "run a command, hold a key, run another" sends both commands
 * in the same tick and starts the hold after both, and the two answers describe the same state. The cost is one request per deferral rather than many at once,
 * which at one command a second is nothing.
 *
 * <p>This class knows nothing of Minecraft, of a mod loader or of the mod. It is the half that would
 * survive being lifted into another project unchanged; keep it free of Minecraft types.
 */
public final class AgentDispatcher {
    /** Ticks between two looks at {@code in.jsonl}: five times a second, against one command a second. */
    public static final int POLL_TICKS = 4;
    /**
     * How many requests may be started in one tick. Requests that answer on the spot are cheap enough
     * to run several of in a row, and the ones that are not take a deferral and stop the run anyway;
     * the cap is only so that a file of a thousand lines cannot stall a single tick. Whatever is left
     * waits for the next tick rather than being refused.
     */
    public static final int MAX_REQUESTS_PER_TICK = 32;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final AgentQueue queue;
    private final Logger log;
    private final Map<String, AgentHandler> handlers = new LinkedHashMap<>();
    private final List<Deferred> deferred = new ArrayList<>();
    /** Lines read from the queue that have not been started yet, oldest first. */
    private final Deque<String> waiting = new ArrayDeque<>();
    private final JsonObject about;

    private long sequence;
    private int sinceLastPoll = POLL_TICKS;
    private boolean started;
    private Runnable onDrained;

    /** Requests of the running script, and how many answers have been written since it started. */
    private int scriptTotal;
    private int answered;
    /** The request started last, for {@link #status}: its id and command. */
    private String currentId;
    private String currentCommand;

    /** {@link System#nanoTime} of the last answer written and of the last tick, for the stall checks. */
    private volatile long lastAnswerNanos = System.nanoTime();
    private volatile long lastTickNanos = System.nanoTime();
    private long stallNanos;
    private Runnable onStall;

    /**
     * @param about what {@code ready.json} says about this process before the command list is added to
     *              it: which node, which Minecraft version, which side
     */
    public AgentDispatcher(AgentQueue queue, Logger log, JsonObject about) {
        this.queue = queue;
        this.log = log;
        this.about = about;
    }

    public AgentQueue queue() {
        return queue;
    }

    /** Registers what one command does. A name registered twice is a mistake in the wiring, not a request. */
    public void register(String command, AgentHandler handler) {
        if (handlers.put(command, handler) != null) {
            throw new IllegalStateException("Two handlers registered for the agent command " + command);
        }
    }

    /** The commands this process answers, in registration order. */
    public List<String> commands() {
        return List.copyOf(handlers.keySet());
    }

    /**
     * Empties the queue and writes {@code ready.json}. Nothing is read before this, so an agent that
     * waits for {@code ready.json} never has a request rotated away underneath it - as long as it can
     * tell this run's {@code ready.json} from the one the last run left in the same directory, which is
     * what {@code startedAt} and {@code pid} are for. Waiting for the file alone is not enough: a game
     * takes the better part of a minute to come up, and for all of it the previous run's file is still
     * sitting there saying the queue is open.
     *
     * @return whether the queue is open: false when another game owns the directory, or it could not be
     *         written
     */
    public boolean start() {
        if (started) return true;
        try {
            queue.open();
        } catch (QueueBusyException e) {
            log.error("[Agent] the queue stays closed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.error("[Agent] could not open the queue in {}", queue.directory(), e);
            return false;
        }
        started = true;
        JsonObject ready = about.deepCopy();
        ready.addProperty("startedAt", System.currentTimeMillis());
        ready.addProperty("pid", ProcessHandle.current().pid());
        JsonArray commands = new JsonArray();
        handlers.keySet().forEach(commands::add);
        ready.add("commands", commands);
        ready.addProperty("in", queue.file(AgentQueue.IN).toAbsolutePath().toString());
        ready.addProperty("out", queue.file(AgentQueue.OUT).toAbsolutePath().toString());
        queue.writeReady(GSON.toJson(ready) + "\n");
        log.info("[Agent] ready, {} commands, queue {}", handlers.size(), queue.directory().toAbsolutePath());
        return true;
    }

    /**
     * Calls {@code onStall}, on the game thread, when requests are waiting and none has been answered
     * for {@code seconds}. For an unattended run, which otherwise sits on a request that will never
     * answer with its window open until someone notices. The longest single request a script may make
     * is a {@code wait} of 1200 ticks, a minute at full speed, so a few minutes is never a slow request;
     * one that runs longer on purpose calls {@link #progress} as it goes.
     */
    public void watchStall(int seconds, Runnable onStall) {
        this.stallNanos = seconds * 1_000_000_000L;
        this.onStall = onStall;
    }

    /**
     * Tells the stall check the request being answered is moving, for one that answers nothing for
     * minutes by design, such as a long {@code server.sprint}.
     */
    public void progress() {
        lastAnswerNanos = System.nanoTime();
    }

    /** {@link System#nanoTime} of the last tick, read by a watchdog on another thread. */
    public long lastTickNanos() {
        return lastTickNanos;
    }

    /**
     * One line saying what the queue is doing, for a person looking at the game: the request being
     * answered, the ticks it still waits, and how far through the script it is. Nothing waiting reads
     * as idle, which is the one state that looks the same as a hang from outside and is not one.
     */
    public String status() {
        if (!started) return "queue closed";
        if (pending() == 0) return onDrained == null && scriptTotal > 0 ? "script done" : "idle, waiting for in.jsonl";
        StringBuilder status = new StringBuilder();
        if (currentId != null) {
            status.append(currentId);
            if (!currentId.equals(currentCommand)) status.append(" (").append(currentCommand).append(')');
        }
        int ticksLeft = 0;
        for (Deferred entry : deferred) ticksLeft = Math.max(ticksLeft, entry.ticks);
        if (ticksLeft > 20) status.append(", ").append(ticksLeft).append(" ticks left");
        if (scriptTotal > 0) status.append(", ").append(Math.min(answered + 1, scriptTotal)).append('/').append(scriptTotal);
        return status.toString();
    }

    /**
     * Runs a file of requests as if the agent had written them, once, at startup. This is what an
     * unattended run is: the launch names a script, the game answers it into {@code out.jsonl}, and
     * {@code whenDone} runs when the last answer has been written.
     *
     * <p>A blank line is skipped and a line starting with {@code //} is a comment, so a script can be
     * read by a person as well as by the parser.
     */
    public void runScript(Path script, Runnable whenDone) {
        if (!started) return;
        List<String> lines;
        try {
            lines = Files.readAllLines(script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Agent] could not read the script {}", script, e);
            if (whenDone != null) whenDone.run();
            return;
        }
        log.info("[Agent] running the script {} ({} lines)", script.toAbsolutePath(), lines.size());
        this.onDrained = whenDone;
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) waiting.add(trimmed);
        }
        scriptTotal = waiting.size();
        answered = 0;
        lastAnswerNanos = System.nanoTime();
        run();
        checkDrained();
    }

    /** Runs {@code work} {@code ticks} ticks from now; anything under one means the next tick. */
    public void defer(int ticks, Runnable work) {
        deferred.add(new Deferred(Math.max(1, ticks), work));
    }

    /**
     * The same, for work that finishes a request: whatever it throws becomes that request's
     * {@code error} rather than only a line in the log, so the agent is never left waiting on an answer
     * that failed on a tick it cannot see.
     */
    public void defer(int ticks, AgentReply reply, Runnable work) {
        defer(ticks, () -> {
            try {
                work.run();
            } catch (AgentException e) {
                reply.fail(e.getMessage());
            } catch (RuntimeException e) {
                log.error("[Agent] {} failed on a later tick", reply.request().command(), e);
                reply.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    /** How many requests are still to be answered: started and deferred, or read and not yet started. */
    public int pending() {
        return deferred.size() + waiting.size();
    }

    /** Called once per game tick, on the side that owns this queue. */
    public void tick() {
        lastTickNanos = System.nanoTime();
        if (!started) return;
        runDeferred();
        if (++sinceLastPoll >= POLL_TICKS) {
            sinceLastPoll = 0;
            // A stop written while the launch's script runs jumps the queue. Behind the script it would
            // wait for every line of it, which is exactly when an agent wants a game gone: the script is
            // stuck, or no longer wanted. Only then: a file drive.py sends is written in one go, and one
            // that ends in stop means after everything else in it.
            String stop = null;
            for (String line : queue.poll()) {
                if (stop == null && onDrained != null && isStop(line)) stop = line;
                else waiting.add(line);
            }
            if (stop != null) {
                // What was read with it waits, and is refused with a reason when the game goes down.
                accept(stop);
                return;
            }
        }
        run();
        checkDrained();
        checkStalled();
    }

    /**
     * Answers whatever is outstanding now, so a shutdown leaves nothing in the queue unanswered: every
     * deferral is run early, and every request still waiting its turn is refused with the reason,
     * rather than never being answered at all.
     */
    public void stop() {
        stop("the game stopped before this request was started");
    }

    /** The same, refusing what is still waiting with {@code reason}. */
    public void stop(String reason) {
        if (!started) return;
        for (Deferred entry : List.copyOf(deferred)) {
            try {
                entry.work.run();
            } catch (RuntimeException e) {
                log.error("[Agent] a deferred answer failed while stopping", e);
            }
        }
        deferred.clear();
        for (String line : waiting) refuse(line, reason);
        waiting.clear();
        started = false;
    }

    /**
     * Starts as many waiting requests as may be started now: none while an earlier one is still
     * deferred, and at most {@link #MAX_REQUESTS_PER_TICK} in one tick.
     */
    private void run() {
        for (int begun = 0; begun < MAX_REQUESTS_PER_TICK; begun++) {
            if (!deferred.isEmpty() || waiting.isEmpty()) return;
            accept(waiting.removeFirst());
        }
    }

    private void runDeferred() {
        if (deferred.isEmpty()) return;
        List<Deferred> due = new ArrayList<>();
        for (Deferred entry : deferred) {
            if (--entry.ticks <= 0) due.add(entry);
        }
        deferred.removeAll(due);
        for (Deferred entry : due) {
            try {
                entry.work.run();
            } catch (RuntimeException e) {
                log.error("[Agent] a deferred answer failed", e);
            }
        }
    }

    private void accept(String line) {
        AgentRequest request;
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) throw new AgentException("a request has to be a JSON object");
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("command")) throw new AgentException("a request needs a 'command'");
            String id = object.has("id") ? object.get("id").getAsString() : String.valueOf(sequence);
            JsonObject arguments = object.has("args") && object.get("args").isJsonObject()
                    ? object.getAsJsonObject("args") : new JsonObject();
            request = new AgentRequest(sequence++, id, object.get("command").getAsString(), arguments);
        } catch (RuntimeException e) {
            refuse(line, e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }

        currentId = request.id();
        currentCommand = request.command();
        AgentReply reply = new AgentReply(request, envelope -> write(GSON.toJson(envelope)));
        AgentHandler handler = handlers.get(request.command());
        if (handler == null) {
            reply.fail("unknown command; ready.json lists the ones this process answers");
            return;
        }
        int deferrals = deferred.size();
        try {
            handler.handle(request, reply);
        } catch (AgentException e) {
            reply.fail(e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[Agent] {} failed", request.command(), e);
            reply.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        // A handler answers now or takes a deferral to answer later. Neither means it dropped the
        // request, and saying so beats leaving the agent waiting for a line that never comes.
        if (!reply.answered() && deferred.size() == deferrals) {
            reply.fail("the handler produced no answer");
        }
    }

    private void refuse(String line, String message) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("id", String.valueOf(sequence++));
        envelope.addProperty("ok", false);
        envelope.addProperty("error", message);
        envelope.addProperty("line", line.length() > 200 ? line.substring(0, 200) + "..." : line);
        write(GSON.toJson(envelope));
    }

    private void write(String line) {
        queue.write(line);
        answered++;
        lastAnswerNanos = System.nanoTime();
    }

    private static boolean isStop(String line) {
        if (!line.contains("stop")) return false;
        try {
            JsonElement parsed = JsonParser.parseString(line);
            return parsed.isJsonObject() && parsed.getAsJsonObject().has("command")
                    && "stop".equals(parsed.getAsJsonObject().get("command").getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void checkStalled() {
        if (onStall == null || pending() == 0 || System.nanoTime() - lastAnswerNanos < stallNanos) return;
        Runnable stalled = onStall;
        onStall = null;
        stalled.run();
    }

    private void checkDrained() {
        if (onDrained == null || !deferred.isEmpty() || !waiting.isEmpty()) return;
        Runnable done = onDrained;
        onDrained = null;
        done.run();
    }

    private static final class Deferred {
        private int ticks;
        private final Runnable work;

        private Deferred(int ticks, Runnable work) {
            this.ticks = ticks;
            this.work = work;
        }
    }
}
