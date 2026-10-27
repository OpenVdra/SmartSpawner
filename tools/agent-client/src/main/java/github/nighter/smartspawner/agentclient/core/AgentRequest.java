package github.nighter.smartspawner.agentclient.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of {@code in.jsonl}, parsed: {@code {"id": "1", "command": "client.state", "args": {...}}}.
 *
 * <p>The accessors exist so a handler never writes argument checking of its own. Each one either
 * returns a usable value or throws an {@link AgentException} that names the command, the argument and
 * what was wrong with it, which is what the agent reading {@code out.jsonl} gets back.
 */
public final class AgentRequest {
    private final long sequence;
    private final String id;
    private final String command;
    private final JsonObject arguments;

    AgentRequest(long sequence, String id, String command, JsonObject arguments) {
        this.sequence = sequence;
        this.id = id;
        this.command = command;
        this.arguments = arguments;
    }

    /** Position in the queue, counted from the first line this run read. Echoed in the reply. */
    public long sequence() {
        return sequence;
    }

    /** Whatever the caller put in {@code id}, echoed in the reply so answers can be matched up. */
    public String id() {
        return id;
    }

    public String command() {
        return command;
    }

    public JsonObject arguments() {
        return arguments;
    }

    public boolean has(String key) {
        return arguments.has(key) && !arguments.get(key).isJsonNull();
    }

    public String string(String key) {
        return primitive(key).getAsString();
    }

    public String string(String key, String fallback) {
        return has(key) ? string(key) : fallback;
    }

    public int integer(String key) {
        try {
            return primitive(key).getAsInt();
        } catch (NumberFormatException e) {
            throw bad(key, "a whole number");
        }
    }

    public int integer(String key, int fallback) {
        return has(key) ? integer(key) : fallback;
    }

    /** An integer argument that has to fall inside {@code min..max}, both ends included. */
    public int integer(String key, int min, int max) {
        int value = integer(key);
        if (value < min || value > max) {
            throw new AgentException(command + ": '" + key + "' has to be between " + min + " and " + max
                    + ", got " + value);
        }
        return value;
    }

    public float decimal(String key, float fallback) {
        if (!has(key)) return fallback;
        try {
            return primitive(key).getAsFloat();
        } catch (NumberFormatException e) {
            throw bad(key, "a number");
        }
    }

    public boolean flag(String key, boolean fallback) {
        return has(key) ? primitive(key).getAsBoolean() : fallback;
    }

    /** A list argument, empty when it is absent. */
    public List<JsonElement> list(String key) {
        if (!has(key)) return List.of();
        JsonElement element = arguments.get(key);
        if (!element.isJsonArray()) throw bad(key, "a list");
        List<JsonElement> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) values.add(item);
        return values;
    }

    /** A list of strings, empty when it is absent. */
    public List<String> strings(String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : list(key)) {
            if (!element.isJsonPrimitive()) throw bad(key, "a list of strings");
            values.add(element.getAsString());
        }
        return values;
    }

    /** A nested object argument, or an empty one when it is absent. */
    public JsonObject object(String key) {
        if (!has(key)) return new JsonObject();
        JsonElement element = arguments.get(key);
        if (!element.isJsonObject()) throw bad(key, "an object");
        return element.getAsJsonObject();
    }

    /** Fails unless the argument is one of {@code allowed}; the message lists them. */
    public String choice(String key, String fallback, String... allowed) {
        String value = string(key, fallback);
        for (String option : allowed) {
            if (option.equals(value)) return value;
        }
        throw new AgentException(command + ": '" + key + "' has to be one of " + String.join(", ", allowed)
                + ", got '" + value + "'");
    }

    private JsonElement primitive(String key) {
        if (!has(key)) throw new AgentException(command + ": '" + key + "' is required");
        JsonElement element = arguments.get(key);
        if (!element.isJsonPrimitive()) throw bad(key, "a plain value");
        return element;
    }

    private AgentException bad(String key, String wanted) {
        JsonElement element = arguments.get(key);
        String shown = element instanceof JsonArray || element instanceof JsonObject ? element.toString()
                : String.valueOf(element);
        return new AgentException(command + ": '" + key + "' has to be " + wanted + ", got " + shown);
    }
}
