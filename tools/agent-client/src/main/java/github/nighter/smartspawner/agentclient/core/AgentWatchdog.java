package github.nighter.smartspawner.agentclient.core;

import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Ends an unattended run whose game thread has stopped ticking: a deadlock, a native dialog, a loop
 * that never returns. The dispatcher's own stall check runs on that thread, so it cannot see this; a
 * frozen game otherwise sits with its window open and its Gradle task running until someone kills it.
 *
 * <p>A daemon thread that looks at {@link AgentDispatcher#lastTickNanos} every few seconds. When the
 * game has not ticked for the limit, it logs where the game thread is stuck, writes one line saying so
 * to {@code out.jsonl}, logs the {@code DONE} line an unattended run always ends with, and halts the
 * process. Halts rather than exits: a frozen game thread may hold the locks that shutdown hooks need,
 * and an exit that waits on them is the same hang again.
 */
public final class AgentWatchdog {
    private AgentWatchdog() { }

    /**
     * Starts watching {@code gameThread}, the thread {@code dispatcher} ticks on, from now until the
     * process ends.
     */
    public static void start(AgentDispatcher dispatcher, Thread gameThread, int seconds, Logger log) {
        long limit = TimeUnit.SECONDS.toNanos(seconds);
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (System.nanoTime() - dispatcher.lastTickNanos() < limit) continue;
                StringBuilder where = new StringBuilder();
                for (StackTraceElement frame : gameThread.getStackTrace()) where.append("\n\tat ").append(frame);
                log.error("[Agent] the game thread has not ticked for {} s; it is stuck at:{}", seconds, where);
                dispatcher.queue().write("{\"id\":\"watchdog\",\"ok\":false,\"error\":\"the game thread has not ticked for "
                        + seconds + " s; the run was halted, and the log has where it was stuck\"}");
                log.error("[Agent] DONE halted, the game froze");
                Runtime.getRuntime().halt(3);
            }
        }, "Agent watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
