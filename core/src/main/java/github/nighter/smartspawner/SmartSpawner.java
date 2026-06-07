package github.nighter.smartspawner;

import github.nighter.smartspawner.api.*;
import github.nighter.smartspawner.bstats.Metrics;
import github.nighter.smartspawner.commands.BrigadierCommandManager;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.near.NearResultGUI;
import github.nighter.smartspawner.commands.near.SpawnerHighlightManager;
import github.nighter.smartspawner.commands.list.gui.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.list.gui.list.SpawnerListGUI;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementHandler;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.commands.list.gui.adminstacker.AdminStackerHandler;
import github.nighter.smartspawner.commands.list.gui.serverselection.ServerSelectionHandler;
import github.nighter.smartspawner.commands.prices.PricesGUI;
import github.nighter.smartspawner.config.Config;
import github.nighter.smartspawner.extras.HopperConfig;
import github.nighter.smartspawner.spawner.config.SpawnerSettingsConfig;
import github.nighter.smartspawner.spawner.config.ItemSpawnerSettingsConfig;
import github.nighter.smartspawner.logging.LoggingConfig;
import github.nighter.smartspawner.logging.SpawnerActionLogger;
import github.nighter.smartspawner.logging.SpawnerAuditListener;
import github.nighter.smartspawner.spawner.natural.NaturalSpawnerListener;
import github.nighter.smartspawner.utils.TimeFormatter;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.SpawnerProvider;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.hooks.IntegrationManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.migration.SpawnerDataMigration;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuFormUI;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerHandler;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageAction;
import github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI;
import github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmListener;
import github.nighter.smartspawner.spawner.interactions.click.SpawnerClickManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerBreakListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerExplosionListener;
import github.nighter.smartspawner.spawner.interactions.place.SpawnerPlaceListener;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import github.nighter.smartspawner.spawner.data.SpawnerFileHandler;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.database.SqliteToMySqlMigration;
import github.nighter.smartspawner.spawner.data.database.YamlToDatabaseMigration;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.lootgen.SpawnerLootGenerator;
import github.nighter.smartspawner.spawner.data.WorldEventHandler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.updates.ConfigUpdater;
import github.nighter.smartspawner.updates.LanguageUpdater;
import github.nighter.smartspawner.updates.LanguageChangelogUpdater;
import github.nighter.smartspawner.updates.UpdateChecker;
import github.nighter.smartspawner.spawner.utils.SpawnerTypeChecker;
import github.nighter.smartspawner.spawner.utils.SpawnerLocationLockManager;

import lombok.Getter;
import lombok.experimental.Accessors;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

@Getter
@Accessors(chain = false)
public class SmartSpawner extends JavaPlugin implements SmartSpawnerPlugin {
    @Getter
    private static SmartSpawner instance;
    public final int DATA_VERSION = 3;
    private final boolean debugMode = getConfig().getBoolean("debug", false);

    // Integration Manager
    private IntegrationManager integrationManager;

    // Services
    private TimeFormatter timeFormatter;
    private ConfigUpdater configUpdater;
    private LanguageManager languageManager;
    private LanguageUpdater languageUpdater;
    private MessageService messageService;
    private SpawnerSettingsConfig spawnerSettingsConfig;
    private ItemSpawnerSettingsConfig itemSpawnerSettingsConfig;

    // Factories
    private SpawnerItemFactory spawnerItemFactory;

    // Core UI components
    private GuiLayoutConfig guiLayoutConfig;
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerMenuFormUI spawnerMenuFormUI;
    private SpawnerStorageUI spawnerStorageUI;
    private FilterConfigUI filterConfigUI;
    private SpawnerStackerUI spawnerStackerUI;
    private SpawnerSellConfirmUI spawnerSellConfirmUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStackerHandler spawnerStackerHandler;
    private SpawnerStorageAction spawnerStorageAction;
    private SpawnerSellManager spawnerSellManager;
    private SpawnerSellConfirmListener spawnerSellConfirmListener;

    // Core managers
    private SpawnerFileHandler spawnerFileHandler;
    private SpawnerStorage spawnerStorage;
    private DatabaseManager databaseManager;
    private SpawnerManager spawnerManager;
    private HopperService hopperService;
    private HopperConfig hopperConfig;
    private SpawnerLocationLockManager spawnerLocationLockManager;

    // Event handlers and utilities
    private NaturalSpawnerListener naturalSpawnerListener;
    private SpawnerLootGenerator spawnerLootGenerator;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerGuiViewManager spawnerGuiViewManager;
    private SpawnerExplosionListener spawnerExplosionListener;
    private SpawnerBreakListener spawnerBreakListener;
    private SpawnerPlaceListener spawnerPlaceListener;
    private WorldEventHandler worldEventHandler;
    private ItemPriceManager itemPriceManager;
    private UpdateChecker updateChecker;
    private BrigadierCommandManager brigadierCommandManager;
    private ListSubCommand listSubCommand;
    private UserPreferenceCache userPreferenceCache;
    private SpawnerListGUI spawnerListGUI;
    private SpawnerManagementHandler spawnerManagementHandler;
    private AdminStackerHandler adminStackerHandler;
    private ServerSelectionHandler serverSelectionHandler;
    private PricesGUI pricesGUI;
    
    // Logging system
    @Getter
    private SpawnerActionLogger spawnerActionLogger;
    private SpawnerAuditListener spawnerAuditListener;
    private LoggingConfig loggingConfig;

    // Near-command highlight manager
    private SpawnerHighlightManager spawnerHighlightManager;
    private NearResultGUI nearResultGUI;

    // API implementation
    private SmartSpawnerAPIImpl apiImpl;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;
        Config.load(this);

        // Initialize plugin integrations
        this.integrationManager = new IntegrationManager(this);
        integrationManager.initializeIntegrations();

        // Check for data migration needs
        migrateDataIfNeeded();

        // Initialize core components
        initializeComponents();

        // Setup plugin infrastructure
        setupCommand();
        setupBtatsMetrics();
        registerListeners();

        // Trigger world event handler to attempt initial spawner loading
        // This is done after all components are initialized
        if (worldEventHandler != null) {
            worldEventHandler.attemptInitialSpawnerLoad();
        }

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("SmartSpawner has been enabled! (Loaded in " + loadTime + "ms)");
    }

    @Override
    public SmartSpawnerAPI getAPI() {
        return apiImpl;
    }

    private void migrateDataIfNeeded() {
        SpawnerDataMigration migration = new SpawnerDataMigration(this);
        if (migration.checkAndMigrateData()) {
            getLogger().info("Data migration completed. Loading with new format...");
        }
    }

    private void initializeComponents() {
        // Initialize services and utilities first since many components depend on them
        initializeServices();
        initializeEconomyComponents();
        initializeCoreComponents();
        initializeHandlers();
        initializeUIAndActions();
        // Initialize hopper handler if enabled in config
        setUpHopperHandler();
        initializeListeners();
        this.apiImpl = new SmartSpawnerAPIImpl(this);
        this.updateChecker = new UpdateChecker(this);
    }

    private void initializeServices() {
        SpawnerTypeChecker.init(this);
        this.timeFormatter = new TimeFormatter(this);
        this.configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        this.languageManager = new LanguageManager(this);
        this.languageUpdater = new LanguageUpdater(this);
        new LanguageChangelogUpdater(this).update();
        this.messageService = new MessageService(this, languageManager);
        
        // Initialize new unified spawner settings config (but don't load yet)
        this.spawnerSettingsConfig = new SpawnerSettingsConfig(this);
        this.itemSpawnerSettingsConfig = new ItemSpawnerSettingsConfig(this);
        
        // Initialize logging system
        this.loggingConfig = new LoggingConfig(this);
        this.spawnerActionLogger = new SpawnerActionLogger(this, loggingConfig);
        this.spawnerAuditListener = new SpawnerAuditListener(spawnerActionLogger);
    }

    private void initializeEconomyComponents() {
        this.itemPriceManager = new ItemPriceManager(this);
        this.itemPriceManager.init();
        
        // Load spawner settings after economy components are ready
        // This is needed because loot configuration requires price manager
        if (spawnerSettingsConfig != null) {
            spawnerSettingsConfig.load();
        }
        
        // Load item spawner settings
        if (itemSpawnerSettingsConfig != null) {
            itemSpawnerSettingsConfig.load();
        }
        
        // Pre-warm the head texture cache after settings are loaded
        // This prevents the brief flash of default player heads when opening GUIs
        SpawnerMobHeadTexture.prewarmCache();

        this.spawnerItemFactory = new SpawnerItemFactory(this);
    }

    private void initializeCoreComponents() {
        // Initialize storage based on configured mode
        initializeStorage();

        this.spawnerManager = new SpawnerManager(this);
        this.spawnerLocationLockManager = new SpawnerLocationLockManager(this);
        this.spawnerManager.reloadAllHolograms();
        this.guiLayoutConfig = new GuiLayoutConfig(this);
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.filterConfigUI = new FilterConfigUI(this);
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerSellConfirmUI = new SpawnerSellConfirmUI(this);
        this.spawnerGuiViewManager = new SpawnerGuiViewManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.spawnerSellManager = new SpawnerSellManager(this);
        this.rangeChecker = new SpawnerRangeChecker(this);

        // Initialize FormUI components only if Floodgate is available
        initializeFormUIComponents();
    }

    private void initializeStorage() {
        String modeStr = getConfig().getString("database.mode", "YAML").toUpperCase();
        StorageMode mode;
        try {
            mode = StorageMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid storage mode '" + modeStr + "', defaulting to YAML");
            mode = StorageMode.YAML;
        }

        if (mode == StorageMode.MYSQL || mode == StorageMode.SQLITE) {
            String dbType = mode == StorageMode.MYSQL ? "MySQL/MariaDB" : "SQLite";
            getLogger().info("Initializing " + dbType + " database storage mode...");
            this.databaseManager = new DatabaseManager(this, mode);

            if (databaseManager.initialize()) {
                SpawnerDatabaseHandler dbHandler = new SpawnerDatabaseHandler(this, databaseManager);
                if (dbHandler.initialize()) {
                    this.spawnerStorage = dbHandler;

                    // Check if migration is enabled in config
                    boolean migrateFromLocal = getConfig().getBoolean("database.migrate_from_local", true);

                    if (migrateFromLocal) {
                        // Check for YAML migration (YAML -> MySQL or YAML -> SQLite)
                        YamlToDatabaseMigration yamlMigration = new YamlToDatabaseMigration(this, databaseManager);
                        if (yamlMigration.needsMigration()) {
                            getLogger().info("YAML data detected, starting migration to " + dbType + "...");
                            if (yamlMigration.migrate()) {
                                getLogger().info("YAML migration completed successfully!");
                            } else {
                                getLogger().warning("YAML migration completed with some errors. Check logs for details.");
                            }
                        }

                        // Check for SQLite to MySQL migration (only when mode is MYSQL)
                        if (mode == StorageMode.MYSQL) {
                            SqliteToMySqlMigration sqliteMigration = new SqliteToMySqlMigration(this, databaseManager);
                            if (sqliteMigration.needsMigration()) {
                                getLogger().info("SQLite data detected, starting migration to MySQL...");
                                if (sqliteMigration.migrate()) {
                                    getLogger().info("SQLite to MySQL migration completed successfully!");
                                } else {
                                    getLogger().warning("SQLite migration completed with some errors. Check logs for details.");
                                }
                            }
                        }
                    } else {
                        debug("Local data migration is disabled in config.");
                    }

                    getLogger().info(dbType + " database storage initialized successfully.");
                } else {
                    getLogger().severe("Failed to initialize database handler, falling back to YAML");
                    databaseManager.shutdown();
                    databaseManager = null;
                    initializeYamlStorage();
                }
            } else {
                getLogger().severe("Failed to initialize database connection, falling back to YAML");
                databaseManager = null;
                initializeYamlStorage();
            }
        } else {
            initializeYamlStorage();
        }
    }

    private void initializeYamlStorage() {
        this.spawnerFileHandler = new SpawnerFileHandler(this);
        this.spawnerStorage = spawnerFileHandler;
        getLogger().info("Using YAML file storage mode.");
    }

    private void initializeFormUIComponents() {
        // Check if FormUI is enabled in config
        boolean formUIEnabled = getConfig().getBoolean("bedrock_support.enable_formui", true);
        
        if (!formUIEnabled) {
            this.spawnerMenuFormUI = null;
            debug("FormUI components not initialized - disabled in config");
            return;
        }
        
        if (integrationManager != null && integrationManager.getFloodgateHook() != null 
            && integrationManager.getFloodgateHook().isEnabled()) {
            try {
                this.spawnerMenuFormUI = new SpawnerMenuFormUI(this);
                getLogger().info("FormUI components initialized successfully for Bedrock player support");
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize FormUI components: " + e.getMessage());
                this.spawnerMenuFormUI = null;
            }
        } else {
            this.spawnerMenuFormUI = null;
            debug("FormUI components not initialized - Floodgate integration not available");
        }
    }

    private void initializeHandlers() {
        this.spawnerStackerUI = new SpawnerStackerUI(this);
        this.spawnEggHandler = new SpawnEggHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.spawnerClickManager = new SpawnerClickManager(this);
        this.spawnerHighlightManager = new SpawnerHighlightManager(this);
        this.nearResultGUI = new NearResultGUI(this, spawnerHighlightManager);
    }

    private void initializeUIAndActions() {
        this.spawnerMenuAction = new SpawnerMenuAction(this);
        this.spawnerStackerHandler = new SpawnerStackerHandler(this);
        this.spawnerStorageAction = new SpawnerStorageAction(this);
        this.spawnerSellConfirmListener = new SpawnerSellConfirmListener(this);
    }

    private void initializeListeners() {
        this.naturalSpawnerListener = new NaturalSpawnerListener(this);
        this.spawnerExplosionListener = new SpawnerExplosionListener(this);
        this.spawnerBreakListener = new SpawnerBreakListener(this);
        this.spawnerPlaceListener = new SpawnerPlaceListener(this);
        this.worldEventHandler = new WorldEventHandler(this);
    }

    public void setUpHopperHandler() {
        this.hopperConfig = new HopperConfig(this);

        if (this.hopperService != null) {
            this.hopperService.cleanup();
        }
        
        if (hopperConfig.isHopperEnabled()) {
            this.hopperService = new HopperService(this);
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Register core listeners
        pm.registerEvents(naturalSpawnerListener, this);
        pm.registerEvents(spawnerBreakListener, this);
        pm.registerEvents(spawnerPlaceListener, this);
        pm.registerEvents(spawnerStorageAction, this);
        pm.registerEvents(spawnerExplosionListener, this);
        // Note: spawnerGuiViewManager registers its own listeners internally
        pm.registerEvents(spawnerClickManager, this);
        pm.registerEvents(spawnerMenuAction, this);
        pm.registerEvents(spawnerStackerHandler, this);
        pm.registerEvents(worldEventHandler, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerManagementHandler, this);
        pm.registerEvents(adminStackerHandler, this);
        pm.registerEvents(serverSelectionHandler, this);
        pm.registerEvents(pricesGUI, this);
        pm.registerEvents(spawnerSellConfirmListener, this);

        // Register near-command listener (player quit cleanup)
        if (spawnerHighlightManager != null) {
            pm.registerEvents(spawnerHighlightManager, this);
        }
        if (nearResultGUI != null) {
            pm.registerEvents(nearResultGUI, this);
        }

        // Register logging listener
        if (spawnerAuditListener != null) {
            pm.registerEvents(spawnerAuditListener, this);
        }
    }

    private void setupCommand() {
        this.brigadierCommandManager = new BrigadierCommandManager(this);
        brigadierCommandManager.registerCommands();
        this.userPreferenceCache = new UserPreferenceCache(this);
        this.listSubCommand = new ListSubCommand(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.spawnerManagementHandler = new SpawnerManagementHandler(this, listSubCommand);
        this.adminStackerHandler = new AdminStackerHandler(this, new SpawnerManagementGUI(this));
        this.serverSelectionHandler = new ServerSelectionHandler(this, listSubCommand);
        this.pricesGUI = new PricesGUI(this);
    }

    private void setupBtatsMetrics() {
        Metrics metrics = new Metrics(this, 24822);
        metrics.addCustomChart(new Metrics.SimplePie("holograms", () ->
                String.valueOf(getConfig().getBoolean("hologram.enabled", false)))
        );
        metrics.addCustomChart(new Metrics.SimplePie("hoppers", () ->
                String.valueOf(getConfig().getBoolean("hopper.enabled", false)))
        );
        metrics.addCustomChart(new Metrics.SimplePie("spawners", () ->
                String.valueOf(this.spawnerManager.getTotalSpawners() / 1000 * 1000))
        );
    }

    public void reload() {
        // reload gui components
        guiLayoutConfig.reloadLayouts();
        
        // Clear spawner info slot cache since layout may have changed
        spawnerGuiViewManager.clearSlotCache();
        
        // Clear GUI item cache since layout/config may have changed
        if (spawnerMenuUI != null) {
            spawnerMenuUI.clearCache();
        }
        
        spawnerStorageAction.loadConfig();
        spawnerStorageUI.reload();
        filterConfigUI.reload();

        // Reload sell confirm UI to update cached layout
        if (spawnerSellConfirmUI != null) {
            spawnerSellConfirmUI.reload();
        }

        // reload services
        integrationManager.reload();
        spawnerMenuAction.reload();
        if (spawnerBreakListener != null) {
            spawnerBreakListener.loadConfig();
        }
        timeFormatter.clearCache();
        
        // Reload spawner settings config (includes mob heads and loot)
        if (spawnerSettingsConfig != null) {
            spawnerSettingsConfig.reload();
            // Clear head cache to force regeneration with new textures
            SpawnerMobHeadTexture.clearCache();
        }
        
        // Reload item spawner settings config
        if (itemSpawnerSettingsConfig != null) {
            itemSpawnerSettingsConfig.reload();
        }
        
        // Reload logging system (file logging + discord webhook)
        loggingConfig.loadConfig();
        spawnerActionLogger.reloadDiscord();
        // Unregister the old listener before registering a fresh one to prevent
        // duplicate event handling and the associated memory leak.
        if (spawnerAuditListener != null) HandlerList.unregisterAll(spawnerAuditListener);
        this.spawnerAuditListener = new SpawnerAuditListener(spawnerActionLogger);
        getServer().getPluginManager().registerEvents(spawnerAuditListener, this);
        
        // Reinitialize FormUI components in case config changed
        initializeFormUIComponents();
    }

    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerMobHeadTexture.clearCache();
        getLogger().info("SmartSpawner has been disabled!");
    }

    private void saveAndCleanup() {
        if (spawnerManager != null) {
            try {
                // Use the storage interface for shutdown
                if (spawnerStorage != null) {
                    spawnerStorage.shutdown();
                }

                // Shutdown database manager if active
                if (databaseManager != null) {
                    databaseManager.shutdown();
                }

                // Clean up the spawner manager
                spawnerManager.cleanupAllSpawners();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving spawner data during shutdown", e);
            }
        }

        if (itemPriceManager != null) {
            itemPriceManager.cleanup();
        }

        // Shutdown logging system
        if (spawnerActionLogger != null) {
            spawnerActionLogger.shutdown();
        }

        // Clean up spawner highlight sessions
        if (spawnerHighlightManager != null) {
            spawnerHighlightManager.cleanup();
        }

        // Clean up resources
        cleanupResources();
    }

    private void cleanupResources() {
        if (rangeChecker != null) rangeChecker.cleanup();
        if (spawnerGuiViewManager != null) spawnerGuiViewManager.cleanup();
        if (hopperService != null) hopperService.cleanup();
        if (spawnerClickManager != null) spawnerClickManager.cleanup();
        if (spawnerStackerHandler != null) spawnerStackerHandler.cleanupAll();
        if (spawnerStorageUI != null) spawnerStorageUI.cleanup();
        if (spawnerLocationLockManager !=null) spawnerLocationLockManager.shutdown();
    }

    // Spawner Provider for ShopGUI+ integration
    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }

    public boolean hasSellIntegration() {
        if (itemPriceManager == null) {
            return false;
        }
        return itemPriceManager.hasSellIntegration();
    }

    public long getTimeFromConfig(String path, String defaultValue) {
        return timeFormatter.getTimeFromConfig(path, defaultValue);
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
