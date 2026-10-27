package github.nighter.smartspawner.agentclient.core;

/**
 * What one command does. Fill in {@code reply} before returning, or keep it and answer from a later
 * tick through {@link AgentDispatcher#defer}; a handler that returns without doing either is reported
 * as a failure rather than leaving the caller waiting forever.
 *
 * <p>Throwing is the normal way to refuse: an {@link AgentException} becomes the reply's {@code error}
 * as written, anything else is logged with its stack trace and reported by class and message.
 */
@FunctionalInterface
public interface AgentHandler {
    void handle(AgentRequest request, AgentReply reply) throws Exception;
}
