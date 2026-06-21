package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.discord.DiscordWebhookConfig;
import github.nighter.smartspawner.logging.discord.DiscordWebhookLogger;
import github.nighter.smartspawner.logging.discord.DiscordEmbedConfigManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Main logging interface for spawner actions.
 * Handles asynchronous logging with file rotation and multiple output formats.
 */
public class SpawnerActionLogger {
    private static final int MAX_DRAIN_PER_TICK = 500;
    private static final double SIZE_RECONCILE_THRESHOLD = 0.9;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ROTATE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final SmartSpawner plugin;
    private final LoggingConfig config;
    private final Queue<SpawnerLogEntry> logQueue;
    private final AtomicBoolean isShuttingDown;
    private final Object fileLock = new Object();
    private final AtomicBoolean rotationPending = new AtomicBoolean(false);

    private Scheduler.Task logTask;
    private volatile DiscordWebhookLogger discordLogger;

    private File currentLogFile;
    private volatile long currentLogFileSize;

    public SpawnerActionLogger(SmartSpawner plugin, LoggingConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.isShuttingDown = new AtomicBoolean(false);

        if (config.isEnabled()) {
            setupLogDirectory();
            startLoggingTask();
        }

        DiscordWebhookConfig discordConfig = new DiscordWebhookConfig(plugin);
        if (discordConfig.isEnabled()) {
            DiscordEmbedConfigManager embedManager = new DiscordEmbedConfigManager(plugin, discordConfig);
            this.discordLogger = new DiscordWebhookLogger(plugin, discordConfig, embedManager);
        }
    }

    /**
     * Logs a spawner action asynchronously.
     */
    public void log(SpawnerLogEntry entry) {
        if (!config.isEnabled() || isShuttingDown.get()) {
            return;
        }
        if (!config.shouldLogEvent(entry.getEventType())) {
            return;
        }

        if (config.isConsoleOutput()) {
            plugin.getLogger().info("[SpawnerLog] " + entry.toReadableString());
        }

        logQueue.offer(entry);

        DiscordWebhookLogger currentDiscord = this.discordLogger;
        if (currentDiscord != null) {
            currentDiscord.queueWebhook(entry);
        }
    }

    /**
     * Logs a spawner action using a builder pattern.
     */
    public void log(SpawnerEventType eventType, LogEntryConsumer consumer) {
        if (!config.isEnabled() || isShuttingDown.get() || !config.shouldLogEvent(eventType)) {
            return;
        }

        SpawnerLogEntry.Builder builder = new SpawnerLogEntry.Builder(eventType);
        consumer.accept(builder);
        log(builder.build());
    }

    @FunctionalInterface
    public interface LogEntryConsumer {
        void accept(SpawnerLogEntry.Builder builder);
    }

    private void setupLogDirectory() {
        try {
            Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());
            Files.createDirectories(logPath);

            String fileName = "spawner-" + LocalDate.now().format(FILE_DATE_FORMAT)
                    + (config.isJsonFormat() ? ".json" : ".log");
            currentLogFile = logPath.resolve(fileName).toFile();
            currentLogFileSize = currentLogFile.exists() ? currentLogFile.length() : 0L;

            rotateLogsIfNeeded();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to setup log directory", e);
        }
    }

    private void startLoggingTask() {
        logTask = Scheduler.runTaskTimerAsync(() -> {
            if (isShuttingDown.get() || logQueue.isEmpty()) {
                return;
            }
            processLogQueue();
        }, 40L, 40L);
    }

    private void processLogQueue() {
        List<SpawnerLogEntry> entries = new ArrayList<>();
        SpawnerLogEntry entry;
        int drained = 0;
        while ((entry = logQueue.poll()) != null && drained < MAX_DRAIN_PER_TICK) {
            entries.add(entry);
            drained++;
        }

        if (!entries.isEmpty()) {
            writeLogEntries(entries);
        }
    }

    private void writeLogEntries(List<SpawnerLogEntry> entries) {
        if (currentLogFile == null || entries.isEmpty()) {
            return;
        }

        synchronized (fileLock) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    currentLogFile.toPath(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                for (SpawnerLogEntry logEntry : entries) {
                    String logLine = config.isJsonFormat() ? logEntry.toJson() : logEntry.toReadableString();
                    writer.write(logLine);
                    writer.newLine();
                    currentLogFileSize += logLine.getBytes(StandardCharsets.UTF_8).length + 1;
                }
                writer.flush();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to write log entries", e);
                return;
            }

            maybeScheduleRotation();
        }
    }

    private void maybeScheduleRotation() {
        if (isShuttingDown.get() || currentLogFile == null) {
            return;
        }

        long maxSizeBytes = config.getMaxLogSizeMB() * 1024L * 1024L;
        if (currentLogFileSize < maxSizeBytes * SIZE_RECONCILE_THRESHOLD) {
            return;
        }

        long actual = currentLogFile.length();
        currentLogFileSize = actual;
        if (actual <= maxSizeBytes) {
            return;
        }

        if (rotationPending.compareAndSet(false, true)) {
            Scheduler.runTaskAsync(() -> {
                try {
                    rotateLog();
                } finally {
                    rotationPending.set(false);
                }
            });
        }
    }

    private void rotateLog() {
        synchronized (fileLock) {
            if (currentLogFile == null || !currentLogFile.exists()) {
                return;
            }

            long maxSizeBytes = config.getMaxLogSizeMB() * 1024L * 1024L;
            if (currentLogFile.length() <= maxSizeBytes) {
                currentLogFileSize = currentLogFile.length();
                return;
            }

            try {
                String timestamp = LocalDateTime.now().format(ROTATE_DATE_FORMAT);
                String extension = config.isJsonFormat() ? ".json" : ".log";
                Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());

                File rotatedFile = logPath.resolve("spawner-" + timestamp + extension).toFile();
                Files.move(currentLogFile.toPath(), rotatedFile.toPath());

                String fileName = "spawner-" + LocalDate.now().format(FILE_DATE_FORMAT) + extension;
                currentLogFile = logPath.resolve(fileName).toFile();
                currentLogFileSize = 0L;

                plugin.getLogger().info("Rotated spawner log to: " + rotatedFile.getName());

                cleanupOldLogs();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to rotate log file", e);
            }
        }
    }

    private void rotateLogsIfNeeded() {
        try {
            Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());

            File[] logFiles = logPath.toFile().listFiles((dir, name) ->
                    name.startsWith("spawner-") && (name.endsWith(".log") || name.endsWith(".json")));

            if (logFiles != null && logFiles.length > config.getMaxLogFiles()) {
                Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

                int filesToDelete = logFiles.length - config.getMaxLogFiles();
                for (int i = 0; i < filesToDelete; i++) {
                    if (logFiles[i].delete()) {
                        plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to rotate old logs", e);
        }
    }

    private void cleanupOldLogs() {
        rotateLogsIfNeeded();
    }

    /**
     * Reloads the Discord webhook logger from {@code discord_logging.yml}.
     * The file-logging task is NOT interrupted; only the Discord side is restarted.
     */
    public void reloadDiscord() {
        DiscordWebhookConfig newDiscordConfig = new DiscordWebhookConfig(plugin);
        DiscordEmbedConfigManager newEmbedManager = new DiscordEmbedConfigManager(plugin, newDiscordConfig);

        if (discordLogger != null) {
            discordLogger.reload(newDiscordConfig, newEmbedManager);
        } else if (newDiscordConfig.isEnabled()) {
            this.discordLogger = new DiscordWebhookLogger(plugin, newDiscordConfig, newEmbedManager);
        }
    }

    /**
     * Flushes remaining log entries and shuts down the logger.
     */
    public void shutdown() {
        isShuttingDown.set(true);

        if (logTask != null) {
            logTask.cancel();
        }

        synchronized (fileLock) {
            processLogQueue();
        }

        DiscordWebhookLogger currentDiscord = discordLogger;
        if (currentDiscord != null) {
            currentDiscord.shutdown();
        }
    }
}
