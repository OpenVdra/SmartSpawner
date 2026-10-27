package github.nighter.smartspawner.agentclient.core;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The exchange itself: two files in one directory, one line of JSON each way.
 *
 * <ul>
 *   <li>{@code in.jsonl} — the agent appends a request per line and never reads it back.</li>
 *   <li>{@code out.jsonl} — this appends a reply per line and never reads it back.</li>
 * </ul>
 *
 * <p>A file queue rather than a socket: there is no port to allocate and no firewall prompt, it works
 * between a game the build launched and an agent that has only file tools, and the whole exchange is
 * still on disk to read afterwards.
 *
 * <p>Reading tracks a byte offset rather than a line count, and stops at the last newline. A request
 * the agent is still half way through writing is therefore left alone until the next poll instead of
 * being parsed as truncated JSON. Nothing here assumes the writer locks the file, because on Windows
 * it does not.
 *
 * <p>One game owns a directory at a time, through an operating system lock on {@code queue.lock} held
 * for as long as the process lives. Two games on one directory each rotate the other's files away
 * and append to the same {@code out.jsonl}, so both runs' answers are worthless; a game left running
 * after its Gradle task was killed is the usual way to get there. The system drops the lock when the
 * process ends, however it ends, so a killed game never leaves a stale one behind the way a pid in a
 * file would.
 */
public final class AgentQueue {
    public static final String IN = "in.jsonl";
    public static final String OUT = "out.jsonl";
    /** Written once the queue is open, so an agent can wait for the game rather than guess. */
    public static final String READY = "ready.json";
    /** Locked by the game that owns the directory; see {@link #open}. */
    public static final String LOCK = "queue.lock";

    private static final Pattern PID = Pattern.compile("\"pid\"\\s*:\\s*(\\d+)");

    private final Path directory;
    private final Logger log;
    private long offset;
    private boolean broken;
    /** Held, never released, for as long as this process owns the directory. */
    private FileLock lock;

    public AgentQueue(Path directory, Logger log) {
        this.directory = directory;
        this.log = log;
    }

    public Path directory() {
        return directory;
    }

    public Path file(String name) {
        return directory.resolve(name);
    }

    /**
     * Empties the queue for a fresh run, keeping the previous one beside it as {@code previous-in.jsonl}
     * and {@code previous-out.jsonl}. Starting at offset zero on a file the last run half consumed would
     * replay its requests; truncating without keeping a copy would throw away the evidence.
     *
     * <p>Takes the directory's lock first, before anything is rotated, so a second game started on the
     * same directory changes nothing and can say who owns it, from the owner's {@code ready.json}.
     *
     * @throws QueueBusyException when another process holds the lock
     */
    public void open() throws IOException {
        Files.createDirectories(directory);
        lock();
        // Taken away before anything else, so that for the moment the queue is being emptied there is
        // no file saying it is open. It is written again, with this run's own stamp, by the dispatcher.
        Files.deleteIfExists(file(READY));
        rotate(IN);
        rotate(OUT);
        Files.writeString(file(IN), "", StandardCharsets.UTF_8);
        Files.writeString(file(OUT), "", StandardCharsets.UTF_8);
        offset = 0L;
    }

    /** Every complete line written since the last call, in order. */
    public List<String> poll() {
        if (broken) return List.of();
        Path in = file(IN);
        try {
            if (!Files.isRegularFile(in)) return List.of();
            long size = Files.size(in);
            if (size == offset) return List.of();
            if (size < offset) {
                // The agent replaced the file instead of appending to it. Start over rather than
                // reading from the middle of a line.
                log.warn("[Agent] {} shrank, reading it from the start again", in);
                offset = 0L;
            }
            byte[] all = Files.readAllBytes(in);
            int end = lastNewline(all);
            if (end <= offset) return List.of();
            String chunk = new String(all, (int) offset, (int) (end - offset), StandardCharsets.UTF_8);
            offset = end;
            List<String> lines = new ArrayList<>();
            for (String line : chunk.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
            return lines;
        } catch (IOException e) {
            log.error("[Agent] could not read {}; the queue is now closed", in, e);
            broken = true;
            return List.of();
        }
    }

    /** Appends one line to {@code out.jsonl}. */
    public void write(String line) {
        try {
            Files.writeString(file(OUT), line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[Agent] could not write to {}", file(OUT), e);
        }
    }

    /** Writes {@code ready.json}, replacing whatever a previous run left. */
    public void writeReady(String json) {
        try {
            Files.writeString(file(READY), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Agent] could not write {}", file(READY), e);
        }
    }

    private void lock() throws IOException {
        if (lock != null) return;
        FileChannel channel = FileChannel.open(file(LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock taken;
        try {
            taken = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            taken = null;
        }
        if (taken == null) {
            channel.close();
            throw new QueueBusyException(directory, ownerPid());
        }
        lock = taken;
    }

    /** The pid the owner's {@code ready.json} names, or -1 when there is none to read yet. */
    private long ownerPid() {
        try {
            Matcher matcher = PID.matcher(Files.readString(file(READY), StandardCharsets.UTF_8));
            return matcher.find() ? Long.parseLong(matcher.group(1)) : -1L;
        } catch (IOException | RuntimeException e) {
            return -1L;
        }
    }

    private void rotate(String name) throws IOException {
        Path file = file(name);
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return;
        Files.move(file, directory.resolve("previous-" + name), StandardCopyOption.REPLACE_EXISTING);
    }

    private static int lastNewline(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] == '\n') return i + 1;
        }
        return 0;
    }
}
