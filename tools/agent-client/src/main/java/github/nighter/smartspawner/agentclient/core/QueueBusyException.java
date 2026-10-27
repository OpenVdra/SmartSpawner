package github.nighter.smartspawner.agentclient.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Another game process already owns this queue directory. Two games answering one {@code in.jsonl}
 * interleave their answers in one {@code out.jsonl}, and each rotates the other's files away when it
 * starts, so every result of both runs is worthless; refusing the second is the only safe answer.
 */
public final class QueueBusyException extends IOException {
    public QueueBusyException(Path directory, long ownerPid) {
        super("another game" + (ownerPid > 0 ? ", pid " + ownerPid + "," : "") + " already owns the agent queue in "
                + directory.toAbsolutePath() + "; close it first, or give this one a queue of its own with "
                + "-Dsmartspawner.agent=<name>");
    }
}
