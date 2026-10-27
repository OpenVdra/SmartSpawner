package github.nighter.smartspawner.agentclient.core;

/**
 * A request that cannot be answered: an unknown command, a missing or unusable argument, or a probe
 * whose subject is not there. The message becomes the {@code error} field of the reply, so it is
 * written for whoever reads {@code out.jsonl}, not for a stack trace.
 */
public final class AgentException extends RuntimeException {
    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
