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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Main logging interface for spawner actions.
 * Handles asynchronous logging with file rotation and multiple output formats.
 */
public class SpawnerActionLogger {
    private final SmartSpawner plugin;
    private final LoggingConfig config;
    private final Queue<SpawnerLogEntry> logQueue;
    private final AtomicBoolean isShuttingDown;
    private Scheduler.Task logTask;
    private DiscordWebhookLogger discordLogger;
    
    private File currentLogFile;
    private static final ThreadLocal<SimpleDateFormat> dateFormat = 
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
    
    public SpawnerActionLogger(SmartSpawner plugin, LoggingConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.isShuttingDown = new AtomicBoolean(false);
        
        if (config.isEnabled()) {
            setupLogDirectory();
            startLoggingTask();
        }
        
        // Initialize Discord webhook logger (only when enabled – no memory cost otherwise)
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
        if (!config.isEnabled() || !config.shouldLogEvent(entry.getEventType())) {
            return;
        }
        
        if (config.isConsoleOutput()) {
            plugin.getLogger().info("[SpawnerLog] " + entry.toReadableString());
        }
        
        // Always use async logging
        logQueue.offer(entry);
        
        // Also send to Discord if enabled
        if (discordLogger != null) {
            discordLogger.queueWebhook(entry);
        }
    }
    
    /**
     * Logs a spawner action using a builder pattern.
     */
    public void log(SpawnerEventType eventType, LogEntryConsumer consumer) {
        if (!config.isEnabled() || !config.shouldLogEvent(eventType)) {
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
            
            String fileName = "spawner-" + dateFormat.get().format(new Date()) + 
                    (config.isJsonFormat() ? ".json" : ".log");
            currentLogFile = logPath.resolve(fileName).toFile();
            
            // Perform log rotation if needed
            rotateLogsIfNeeded();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to setup log directory", e);
        }
    }
    
    private void startLoggingTask() {
        // Process log queue every 2 seconds (always async)
        logTask = Scheduler.runTaskTimerAsync(() -> {
            if (isShuttingDown.get()) {
                return;
            }
            processLogQueue();
        }, 40L, 40L);
    }
    
    private void processLogQueue() {
        if (logQueue.isEmpty()) {
            return;
        }
        
        List<SpawnerLogEntry> entries = new ArrayList<>();
        SpawnerLogEntry entry;
        while ((entry = logQueue.poll()) != null) {
            entries.add(entry);
        }
        
        if (!entries.isEmpty()) {
            writeLogEntries(entries);
        }
    }
    
    private void writeLogEntries(List<SpawnerLogEntry> entries) {
        if (currentLogFile == null || entries.isEmpty()) {
            return;
        }
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentLogFile, true))) {
            for (SpawnerLogEntry entry : entries) {
                String logLine = config.isJsonFormat() ? entry.toJson() : entry.toReadableString();
                writer.write(logLine);
                writer.newLine();
            }
            writer.flush();
            
            // Check if rotation is needed after writing
            checkAndRotateLog();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write log entries", e);
        }
    }
    
    private void checkAndRotateLog() {
        if (currentLogFile == null || !currentLogFile.exists()) {
            return;
        }
        
        long fileSizeBytes = currentLogFile.length();
        long maxSizeBytes = config.getMaxLogSizeMB() * 1024 * 1024;
        
        if (fileSizeBytes > maxSizeBytes) {
            rotateLog();
        }
    }
    
    private void rotateLog() {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String extension = config.isJsonFormat() ? ".json" : ".log";
            Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());
            
            File rotatedFile = logPath.resolve("spawner-" + timestamp + extension).toFile();
            Files.move(currentLogFile.toPath(), rotatedFile.toPath());
            
            String fileName = "spawner-" + dateFormat.get().format(new Date()) + extension;
            currentLogFile = logPath.resolve(fileName).toFile();
            
            plugin.getLogger().info("Rotated spawner log to: " + rotatedFile.getName());
            
            // Clean up old logs
            cleanupOldLogs();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to rotate log file", e);
        }
    }
    
    private void rotateLogsIfNeeded() {
        try {
            Path logPath = Paths.get(plugin.getDataFolder().getAbsolutePath(), config.getLogDirectory());
            
            File[] logFiles = logPath.toFile().listFiles((dir, name) -> 
                    name.startsWith("spawner-") && (name.endsWith(".log") || name.endsWith(".json")));
            
            if (logFiles != null && logFiles.length > config.getMaxLogFiles()) {
                // Sort by last modified date
                Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));
                
                // Delete oldest files
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
            // Hot-reload: swap config and restart background task if needed
            discordLogger.reload(newDiscordConfig, newEmbedManager);
        } else if (newDiscordConfig.isEnabled()) {
            // Discord was disabled before; create a fresh logger now
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
        
        // Flush remaining entries
        processLogQueue();
        
        // Shutdown Discord logger
        if (discordLogger != null) {
            discordLogger.shutdown();
        }
    }
}
