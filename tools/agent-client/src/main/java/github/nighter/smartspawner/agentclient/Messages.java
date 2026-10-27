package github.nighter.smartspawner.agentclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * What the server told the player as text: every chat line, action bar, title and subtitle, logged by
 * {@code ChatComponentMixin} and {@code HudMixin} as it arrives.
 *
 * <p>A plugin speaks through these four, and the action bar fades in three seconds, so reading them off
 * a capture means racing the fade and squinting at colours. Logged here, a check reads the message and
 * its styles as values, whenever it gets round to asking. Each entry has a sequence number, so a script
 * asks for what arrived {@code since} the number it saw last. {@code client.clean} clears the chat on
 * screen, not this log.
 */
public final class Messages {
    static final String CHAT = "chat";
    static final String ACTION_BAR = "action_bar";
    static final String TITLE = "title";
    static final String SUBTITLE = "subtitle";
    static final Set<String> KINDS = Set.of(CHAT, ACTION_BAR, TITLE, SUBTITLE);

    /** Enough for a long script; the oldest go first. */
    private static final int CAPACITY = 500;

    private record Entry(long sequence, String kind, Component message) { }

    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static long sequence;

    private Messages() { }

    /** Called from the mixins, on the client thread. */
    public static synchronized void record(String kind, Component message) {
        if (message == null) return;
        entries.addLast(new Entry(++sequence, kind, message));
        while (entries.size() > CAPACITY) entries.removeFirst();
    }

    /** The entries after {@code since} of the given kinds (all when empty), oldest first. */
    static synchronized JsonObject since(long since, Set<String> kinds) {
        JsonArray messages = new JsonArray();
        for (Entry entry : entries) {
            if (entry.sequence() <= since || (!kinds.isEmpty() && !kinds.contains(entry.kind()))) continue;
            JsonObject line = Styled.json(entry.message());
            line.addProperty("sequence", entry.sequence());
            line.addProperty("kind", entry.kind());
            messages.add(line);
        }
        JsonObject result = new JsonObject();
        result.addProperty("last", sequence);
        result.add("messages", messages);
        return result;
    }

    static synchronized void clear() {
        entries.clear();
    }
}
