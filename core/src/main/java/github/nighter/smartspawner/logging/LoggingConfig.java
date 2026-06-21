package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for the spawner logging system.
 * Controls what events are logged and how they're formatted.
 */
public class LoggingConfig {
    private static final String LOG_DIRECTORY = "logs";

    private final SmartSpawner plugin;
    @Getter
    private boolean enabled;
    @Getter
    private boolean jsonFormat;
    @Getter
    private boolean consoleOutput;
    private Set<SpawnerEventType> enabledEvents;
    @Getter
    private String logDirectory;
    @Getter
    private int maxLogFiles;
    @Getter
    private long maxLogSizeMB;
    @Getter
    private boolean logAllEvents;
    @Getter
    private List<String> loggedEvents;
    
    public LoggingConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        this.jsonFormat = plugin.getConfig().getBoolean("logging.json_format", false);
        this.consoleOutput = plugin.getConfig().getBoolean("logging.console_output", false);
        this.logDirectory = LOG_DIRECTORY;
        this.maxLogFiles = plugin.getConfig().getInt("logging.max_log_files", 10);
        this.maxLogSizeMB = plugin.getConfig().getLong("logging.max_log_size_mb", 10);
        this.logAllEvents = plugin.getConfig().getBoolean("logging.log_all_events", false);
        this.loggedEvents = plugin.getConfig().getStringList("logging.logged_events");

        // Parse enabled events
        this.enabledEvents = parseEnabledEvents();
    }
    
    private Set<SpawnerEventType> parseEnabledEvents() {
        Set<SpawnerEventType> events = EnumSet.noneOf(SpawnerEventType.class);
        
        // Check if we should log all events
        if (logAllEvents) {
            return EnumSet.allOf(SpawnerEventType.class);
        }
        
        // Parse specific event types
        if (loggedEvents == null || loggedEvents.isEmpty()) {
            // Default to logging major events
            events.add(SpawnerEventType.SPAWNER_PLACE);
            events.add(SpawnerEventType.SPAWNER_BREAK);
            events.add(SpawnerEventType.SPAWNER_EXPLODE);
            events.add(SpawnerEventType.SPAWNER_STACK_HAND);
            events.add(SpawnerEventType.SPAWNER_STACK_GUI);
            events.add(SpawnerEventType.SPAWNER_DESTACK_GUI);
            events.add(SpawnerEventType.SPAWNER_EXP_CLAIM);
            events.add(SpawnerEventType.SPAWNER_SELL_ALL);
            events.add(SpawnerEventType.SPAWNER_ITEM_TAKE_ALL);
            events.add(SpawnerEventType.SPAWNER_ITEMS_SORT);
            events.add(SpawnerEventType.SPAWNER_ITEM_FILTER);
            events.add(SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS);
            events.add(SpawnerEventType.COMMAND_EXECUTE_PLAYER);
            events.add(SpawnerEventType.COMMAND_EXECUTE_CONSOLE);
            events.add(SpawnerEventType.COMMAND_EXECUTE_RCON);
            return events;
        }
        
        for (String eventName : loggedEvents) {
            try {
                events.add(SpawnerEventType.valueOf(eventName.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Invalid event type, skip
            }
        }
        
        return events;
    }

    public Set<SpawnerEventType> getEnabledEvents() {
        return new HashSet<>(enabledEvents);
    }

    public boolean shouldLogEvent(SpawnerEventType eventType) {
        return enabled && enabledEvents.contains(eventType);
    }
}
