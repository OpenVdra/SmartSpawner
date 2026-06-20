package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.discord.DiscordWebhookConfig;
import github.nighter.smartspawner.logging.discord.DiscordWebhookLogger;
import github.nighter.smartspawner.logging.discord.DiscordEmbedConfigManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
 * Handles asynchronous logging with decoupled background file rotation.
 */
public class SpawnerActionLogger {
    private final SmartSpawner plugin;
    private final LoggingConfig config;
    private final Queue<SpawnerLogEntry> logQueue;
    private final AtomicBoolean isShuttingDown;
    
    private Scheduler.Task logTask;
    private volatile DiscordWebhookLogger discordLogger;
    private File currentLogFile;
    private long currentLogFileSize;

    // Thread-safe und modernere Alternative zu SimpleDateFormat
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ROTATE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public SpawnerActionLogger(SmartSpawner plugin, LoggingConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.isShuttingDown = new AtomicBoolean(false);

        if (config.isEnabled()) {
            setupLogDirectory();
            startLoggingTask();
            
            // Discord erst laden, wenn die Haupt-Config Logging überhaupt erlaubt
            reloadDiscord();
        }
    }

    /**
     * Logs a spawner action asynchronously.
     */
    public void log(SpawnerLogEntry entry) {
        if (!config.isEnabled() || isShuttingDown.get()) {
            return;
        }

        // FEHLERBEHEBUNG: Logge nur, wenn das Event in der Config auch AKTIVIERT ist
        if (!config.isEventEnabled(entry.getEventType())) {
            return;
        }

        if (config.isConsoleOutput()) {
            plugin.getLogger().info("[SpawnerLog] " + entry.toReadableString());
        }

        logQueue.offer(entry);

        // Discord-Logger via volatile-Feld thread-sicher abfragen
        DiscordWebhookLogger currentDiscord = this.discordLogger;
        if (currentDiscord != null) {
            currentDiscord.queueWebhook(entry);
        }
    }

    /**
     * Logs a spawner action using a builder pattern.
     */
    public void log(SpawnerEventType eventType, LogEntryConsumer consumer) {
        if (!config.isEnabled() || isShuttingDown.get() || !config.isEventEnabled(eventType)) {
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

            String fileName = "spawner-" + LocalDate.now().format(FILE_DATE_FORMAT) + 
                    (config.isJsonFormat() ? ".json" : ".log");
            
            currentLogFile = logPath.resolve(fileName).toFile();
            
            // Initialen Size-Check machen, um I/O im Loop zu verringern
            currentLogFileSize = currentLogFile.exists() ? currentLogFile.length() : 0;

            // Altweltliche Log-Rotation beim Start asynchron triggern
            Scheduler.runTaskAsync(this::rotateLogsIfNeeded);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to setup log directory", e);
        }
    }

    private void startLoggingTask() {
        // Intervall auf 20 Ticks (1 Sekunde) herabgesetzt für flüssigeren I/O-Fluss
        logTask = Scheduler.runTaskTimerAsync(() -> {
            if (!logQueue.isEmpty()) {
                processLogQueue();
            }
        }, 20L, 20L);
    }

    private void processLogQueue() {
        List<SpawnerLogEntry> entries = new ArrayList<>();
        SpawnerLogEntry entry;
        
        // Begrenze maximale Entnahmen pro Durchlauf, um Heap-Spikes zu verhindern
        int drained = 0;
        while ((entry = logQueue.poll()) != null && drained < 500) {
            entries.add(entry);
            drained++;
        }

        if (!entries.isEmpty()) {
            writeLogEntries(entries);
        }
    }

    private void writeLogEntries(List<SpawnerLogEntry> entries) {
        if (currentLogFile == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFile, true))) {
            for (SpawnerLogEntry entry : entries) {
                String logLine = config.isJsonFormat() ? entry.toJson() : entry.toReadableString();
                writer.write(logLine);
                writer.newLine();
                
                // Speicher-Größe im RAM tracken anstatt bei jeder Zeile die Festplatte zu fragen
                currentLogFileSize += logLine.length() + System.lineSeparator().length();
            }
            writer.flush();

            // Wenn kritische Größe überschritten, I/O-schwere Rotation asynchron auslagern
            long maxSizeBytes = (long) config.getMaxLogSizeMB() * 1024 * 1024;
            if (currentLogFileSize > maxSizeBytes) {
                Scheduler.runTaskAsync(this::rotateLog);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write log entries", e);
        }
    }

    private synchronized void rotateLog() {
        // Erneuter Check im synchronisierten Block, um doppelte Rotation zu verhindern
        long maxSizeBytes = (long) config.getMaxLogSizeMB() * 1024 * 1024;
        if (currentLogFile == null || !currentLogFile.exists() || currentLogFile.length() <= maxSizeBytes) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(ROTATE_DATE_FORMAT);
            String extension = config.isJsonFormat() ? ".json" : ".log";
            Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());

            File rotatedFile = logPath.resolve("spawner-" + timestamp + extension).toFile();
            Files.move(currentLogFile.toPath(), rotatedFile.toPath());

            String fileName = "spawner-" + LocalDate.now().format(FILE_DATE_FORMAT) + extension;
            currentLogFile = logPath.resolve(fileName
