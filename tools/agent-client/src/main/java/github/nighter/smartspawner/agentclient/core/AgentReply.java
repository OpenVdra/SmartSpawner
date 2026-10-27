package github.nighter.smartspawner.agentclient.core;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

/**
 * The one answer a request gets. A handler either fills it in before it returns, or keeps it and fills
 * it in from a later tick — which is what holding a key for twenty ticks and then reading the state
 * back needs.
 *
 * <p>Answering twice is ignored rather than an error: a handler that both deferred and threw would
 * otherwise turn one request into two lines of {@code out.jsonl}.
 */
public final class AgentReply {
    private final AgentRequest request;
    private final Consumer<JsonObject> sink;
    private final long started = System.nanoTime();
    private boolean answered;

    AgentReply(AgentRequest request, Consumer<JsonObject> sink) {
        this.request = request;
        this.sink = sink;
    }

    public AgentRequest request() {
        return request;
    }

    public boolean answered() {
        return answered;
    }

    /** Answers with {@code result}. */
    public void ok(JsonObject result) {
        write(true, result, null);
    }

    /** Answers with nothing but success, for a command that only does something. */
    public void ok() {
        ok(new JsonObject());
    }

    /** Answers with a failure. The message is for whoever reads the queue, so say what went wrong. */
    public void fail(String message) {
        write(false, null, message);
    }

    private void write(boolean ok, JsonObject result, String error) {
        if (answered) return;
        answered = true;
        JsonObject envelope = new JsonObject();
        envelope.addProperty("id", request.id());
        envelope.addProperty("sequence", request.sequence());
        envelope.addProperty("command", request.command());
        envelope.addProperty("ok", ok);
        if (result != null) envelope.add("result", result);
        if (error != null) envelope.addProperty("error", error);
        envelope.addProperty("tookMs", Math.round((System.nanoTime() - started) / 1_000_000.0 * 10.0) / 10.0);
        sink.accept(envelope);
    }
}
