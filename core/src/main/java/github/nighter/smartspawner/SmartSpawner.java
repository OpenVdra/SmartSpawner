package github.nighter.smartspawner;

import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerAPIImpl;
import github.nighter.smartspawner.api.SmartSpawnerPlugin;
import github.nighter.smartspawner.api.gui.ExternalGuiLayoutLoader;
import github.nighter.smartspawner.api.gui.GuiLayoutRegistryImpl;
import github.nighter.smartspawner.commands.BrigadierCommandManager;
import github.nighter.smartspawner.commands.editloot.LootEditorDialogs;
import github.nighter.smartspawner.commands.editloot.LootEditorHandler;
import github.nighter.smartspawner.commands.editloot.LootEditorService;
import github.nighter.smartspawner.commands.editloot.LootEditorUI;
import github.nighter.smartspawner.commands.list.ListSubCommand;
import github.nighter.smartspawner.commands.list.gui.list.SpawnerListGUI;
import github.nighter.smartspawner.commands.list.gui.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementHandler;
import github.nighter.smartspawner.commands.list.gui.serverselection.ServerSelectionHandler;
import github.nighter.smartspawner.commands.near.NearResultGUI;
import github.nighter.smartspawner.commands.near.SpawnerHighlightManager;
import github.nighter.smartspawner.commands.prices.PricesGUI;
import github.nighter.smartspawner.config.Config;
import github.nighter.smartspawner.extras.HopperConfig;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.hooks.IntegrationManager;
import github.nighter.smartspawner.hooks.economy.ItemPriceManager;
import github.nighter.smartspawner.hooks.economy.SellIntegrationConfigUpdater;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.SpawnerProvider;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.logging.ActivityLogConfigUpdater;
import github.nighter.smartspawner.logging.LoggingConfig;
import github.nighter.smartspawner.logging.SpawnerActionLogger;
import github.nighter.smartspawner.logging.SpawnerAuditListener;
import github.nighter.smartspawner.migration.SpawnerDataMigration;
import github.nighter.smartspawner.spawner.config.ItemSpawnerSettingsConfig;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.config.SpawnerSettingsConfig;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.data.WorldEventHandler;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.database.SqliteToMySqlMigration;
import github.nighter.smartspawner.spawner.data.database.YamlToDatabaseMigration;
import github.nighter.smartspawner.spawner.data.storage.SpawnerStorage;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.layout.GuiButtonInteractionService;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmListener;
import github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageAction;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.interactions.click.SpawnerClickManager;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerBreakListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerExplosionListener;
import github.nighter.smartspawner.spawner.interactions.destroy.SpawnerRemovalService;
import github.nighter.smartspawner.spawner.interactions.place.SpawnerPlaceListener;
import github.nighter.smartspawner.spawner.interactions.stack.SpawnerStackHandler;
import github.nighter.smartspawner.spawner.interactions.type.SpawnEggHandler;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.lootgen.SpawnerLootGenerator;
import github.nighter.smartspawner.spawner.lootgen.SpawnerRangeChecker;
import github.nighter.smartspawner.spawner.natural.NaturalSpawnerListener;
import github.nighter.smartspawner.spawner.model.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import github.nighter.smartspawner.spawner.utils.SpawnerTypeChecker;
import github.nighter.smartspawner.updates.ConfigUpdater;
import github.nighter.smartspawner.updates.LanguageUpdater;
import github.nighter.smartspawner.updates.UpdateChecker;
import github.nighter.smartspawner.utils.TimeFormatter;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

@Getter
@Accessors(chain = false)
public class SmartSpawner extends JavaPlugin implements SmartSpawnerPlugin {
    @Getter
    private static SmartSpawner instance;
    public final int DATA_VERSION = 3;

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
    private ExternalGuiLayoutLoader guiLayoutLoader;
    private GuiLayoutRegistryImpl guiLayoutRegistry;

    // Core UI components
    private GuiLayoutConfig guiLayoutConfig;
    private GuiButtonInteractionService guiButtonInteractionService;
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerStorageUI spawnerStorageUI;
    private FilterConfigUI filterConfigUI;
    private SpawnerSellConfirmUI spawnerSellConfirmUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStorageAction spawnerStorageAction;
    private SpawnerSellManager spawnerSellManager;
    private SpawnerSellConfirmListener spawnerSellConfirmListener;

    // Core managers
    private SpawnerStorage spawnerStorage;
    private DatabaseManager databaseManager;
    private SpawnerManager spawnerManager;
    private HopperService hopperService;
    private HopperConfig hopperConfig;
    private SpawnerRemovalService spawnerRemovalService;

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
    private ServerSelectionHandler serverSelectionHandler;
    private PricesGUI pricesGUI;

    // In-game loot editor (/ss editloot)
    private LootEditorService lootEditorService;
    private LootEditorUI lootEditorUI;
    private LootEditorDialogs lootEditorDialogs;
    private LootEditorHandler lootEditorHandler;

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
        if (!initializeComponents()) {
            getLogger().severe("SmartSpawner could not open its spawner storage and will not enable. "
                    + "Fix the database settings in config.yml, then restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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

    private boolean initializeComponents() {
        // Initialize services and utilities first since many components depend on them
        initializeServices();
        initializeEconomyComponents();
        if (!initializeCoreComponents()) {
            return false;
        }
        initializeHandlers();
        initializeUIAndActions();
        // Initialize hopper handler if enabled in config
        setUpHopperHandler();
        initializeListeners();
        this.apiImpl = new SmartSpawnerAPIImpl(this);
        this.updateChecker = new UpdateChecker(this);
        return true;
    }

    private void initializeServices() {
        SpawnerTypeChecker.init(this);
        this.timeFormatter = new TimeFormatter(this);
        this.configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        this.languageManager = new LanguageManager(this);
        this.languageUpdater = new LanguageUpdater(this);
        this.messageService = new MessageService(this, languageManager);
        
        // Initialize new unified spawner settings config (but don't load yet)
        this.spawnerSettingsConfig = new SpawnerSettingsConfig(this);
        this.itemSpawnerSettingsConfig = new ItemSpawnerSettingsConfig(this);
        
        // sell_integration.yml, before ItemPriceManager reads it in initializeEconomyComponents().
        new SellIntegrationConfigUpdater(this).checkAndUpdate();

        // Initialize logging system. The updater has to run first, it owns activity_log.yml.
        new ActivityLogConfigUpdater(this).checkAndUpdate();
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

    private boolean initializeCoreComponents() {
        // Initialize storage based on configured mode
        if (!initializeStorage()) {
            return false;
        }

        this.spawnerManager = new SpawnerManager(this);
        this.spawnerRemovalService = new SpawnerRemovalService(this);
        this.spawnerManager.reloadAllHolograms();
        this.guiLayoutLoader = new ExternalGuiLayoutLoader(this);
        this.guiLayoutRegistry = new GuiLayoutRegistryImpl(guiLayoutLoader, getLogger());
        this.guiLayoutConfig = new GuiLayoutConfig(this, guiLayoutLoader, guiLayoutRegistry);
        this.guiButtonInteractionService = new GuiButtonInteractionService(this);
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.filterConfigUI = new FilterConfigUI(this);
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerSellConfirmUI = new SpawnerSellConfirmUI(this);
        this.spawnerGuiViewManager = new SpawnerGuiViewManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.spawnerSellManager = new SpawnerSellManager(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
        return true;
    }

    /**
     * Brings up the configured storage backend.
     * <p>
     * There is deliberately no fallback. YAML storage is gone, so a backend that cannot be opened
     * leaves the plugin with nowhere to persist spawners, and running anyway would silently discard
     * every change on the next restart. Refusing to enable is the safe outcome.
     *
     * @return true when spawner storage is ready to use
     */
    private boolean initializeStorage() {
        String modeStr = getConfig().getString("database.type", "SQLITE");
        StorageMode mode = StorageMode.fromConfig(modeStr);
        if (!mode.name().equalsIgnoreCase(modeStr == null ? "" : modeStr.trim())) {
            getLogger().warning("Storage mode '" + modeStr + "' is not available, using " + mode + " instead.");
        }

        String dbType = mode == StorageMode.MYSQL ? "MySQL/MariaDB" : "SQLite";
        getLogger().info("Initializing " + dbType + " database storage mode...");
        this.databaseManager = new DatabaseManager(this, mode);

        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize the " + dbType + " connection. Spawner data cannot be loaded or saved.");
            databaseManager.shutdown();
            databaseManager = null;
            return false;
        }

        SpawnerDatabaseHandler dbHandler = new SpawnerDatabaseHandler(this, databaseManager);
        if (!dbHandler.initialize()) {
            getLogger().severe("Failed to initialize the " + dbType + " storage handler. Spawner data cannot be loaded or saved.");
            databaseManager.shutdown();
            databaseManager = null;
            return false;
        }

        this.spawnerStorage = dbHandler;

        // Check if migration is enabled in config
        boolean migrateFromLocal = getConfig().getBoolean("database.migrate-from-local", true);

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
        }

        getLogger().info(dbType + " database storage initialized successfully.");
        return true;
    }

    private void initializeHandlers() {
        this.spawnEggHandler = new SpawnEggHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.spawnerClickManager = new SpawnerClickManager(this);
        this.spawnerHighlightManager = new SpawnerHighlightManager(this);
        this.nearResultGUI = new NearResultGUI(this, spawnerHighlightManager);
    }

    private void initializeUIAndActions() {
        this.spawnerMenuAction = new SpawnerMenuAction(this);
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
        pm.registerEvents(worldEventHandler, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerManagementHandler, this);
        pm.registerEvents(serverSelectionHandler, this);
        pm.registerEvents(pricesGUI, this);
        pm.registerEvents(lootEditorHandler, this);
        pm.registerEvents(spawnerSellConfirmListener, this);
        pm.registerEvents(guiButtonInteractionService, this);

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
        // Built before the command manager because MainCommand takes the loot editor at construction.
        this.lootEditorService = new LootEditorService(this);
        this.lootEditorUI = new LootEditorUI(this, lootEditorService);
        this.lootEditorDialogs = new LootEditorDialogs(this, lootEditorService, lootEditorUI);
        this.lootEditorHandler = new LootEditorHandler(this, lootEditorService, lootEditorUI, lootEditorDialogs);

        this.brigadierCommandManager = new BrigadierCommandManager(this);
        brigadierCommandManager.registerCommands();
        this.userPreferenceCache = new UserPreferenceCache(this);
        this.listSubCommand = new ListSubCommand(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.spawnerManagementHandler = new SpawnerManagementHandler(this, listSubCommand);
        this.serverSelectionHandler = new ServerSelectionHandler(this, listSubCommand);
        this.pricesGUI = new PricesGUI(this);
    }

    private void setupBtatsMetrics() {
        Metrics metrics = new Metrics(this, 24822);

        // --- Feature toggles ---
        metrics.addCustomChart(new SimplePie("holograms", () ->
                String.valueOf(getConfig().getBoolean("hologram.enabled", false))));

        metrics.addCustomChart(new SimplePie("hoppers", () ->
                String.valueOf(getConfig().getBoolean("hopper.enabled", false))));

        // Number of spawner *blocks* placed (each record = 1 block, regardless of stack size)
        metrics.addCustomChart(new SimplePie("spawner_blocks", () ->
                bucketSpawnerCount(spawnerManager.getTotalSpawners())));

        // Total stacked spawners across all worlds (sum of every block's stack size)
        metrics.addCustomChart(new SimplePie("stacked_spawners", () -> {
            long totalStacked = spawnerManager.getAllSpawners().stream()
                    .mapToLong(SpawnerData::getStackSize)
                    .sum();
            return bucketSpawnerCount(totalStacked);
        }));

        // --- Storage backend ---
        metrics.addCustomChart(new SimplePie("storage_mode", () ->
                getConfig().getString("database.type", "SQLITE")));

        // --- Language & GUI layout ---
        metrics.addCustomChart(new SimplePie("language", () ->
                getConfig().getString("language", "en_US")));

        metrics.addCustomChart(new SimplePie("gui_layout", () ->
                getConfig().getString("gui_layout", "default")));

        // --- Protection plugin integrations ---
        metrics.addCustomChart(new AdvancedPie("protection_plugins", () -> {
            Map<String, Integer> map = new HashMap<>();
            if (integrationManager.isHasWorldGuard())        map.put("WorldGuard", 1);
            if (integrationManager.isHasTowny())             map.put("Towny", 1);
            if (integrationManager.isHasLands())             map.put("Lands", 1);
            if (integrationManager.isHasGriefPrevention())   map.put("GriefPrevention", 1);
            if (integrationManager.isHasSuperiorSkyblock2()) map.put("SuperiorSkyblock2", 1);
            if (integrationManager.isHasBentoBox())          map.put("BentoBox", 1);
            if (integrationManager.isHasIridiumSkyblock())   map.put("IridiumSkyblock", 1);
            if (integrationManager.isHasPlotSquared())       map.put("PlotSquared", 1);
            if (integrationManager.isHasResidence())         map.put("Residence", 1);
            if (integrationManager.isHasMinePlots())         map.put("MinePlots", 1);
            if (integrationManager.isHasSimpleClaimSystem()) map.put("SimpleClaimSystem", 1);
            if (map.isEmpty()) map.put("None", 1);
            return map;
        }));
    }

    /** Bucket a spawner/stack count into a human-readable range label (supports up to ~100M). */
    private static String bucketSpawnerCount(long value) {
        if (value == 0)               return "0";
        if (value <= 100)             return "1-100";
        if (value <= 500)             return "101-500";
        if (value <= 1_000)           return "501-1K";
        if (value <= 5_000)           return "1K-5K";
        if (value <= 10_000)          return "5K-10K";
        if (value <= 50_000)          return "10K-50K";
        if (value <= 100_000)         return "50K-100K";
        if (value <= 500_000)         return "100K-500K";
        if (value <= 1_000_000)       return "500K-1M";
        if (value <= 5_000_000)       return "1M-5M";
        if (value <= 10_000_000)      return "5M-10M";
        if (value <= 50_000_000)      return "10M-50M";
        if (value <= 100_000_000)     return "50M-100M";
        return "100M+";
    }

    public void reload() {
        // reload gui components
        guiLayoutConfig.reloadLayouts();
        
        // Clear spawner info slot cache since layout may have changed
        
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

        // Keep loot-editor navigation reading fresh snapshots after a full plugin reload.
        if (lootEditorService != null) {
            lootEditorService.reload();
        }

        // Reload logging system (file logging + discord webhook)
        loggingConfig.loadConfig();
        spawnerActionLogger.reloadDiscord();
        // Unregister the old listener before registering a fresh one to prevent
        // duplicate event handling and the associated memory leak.
        if (spawnerAuditListener != null) HandlerList.unregisterAll(spawnerAuditListener);
        this.spawnerAuditListener = new SpawnerAuditListener(spawnerActionLogger);
        getServer().getPluginManager().registerEvents(spawnerAuditListener, this);
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
        if (spawnerStorageUI != null) spawnerStorageUI.cleanup();
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
}
