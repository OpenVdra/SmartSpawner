package github.nighter.smartspawner.commands.list;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.commands.list.gui.CrossServerSpawnerData;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import github.nighter.smartspawner.commands.list.gui.list.SpawnerListHolder;
import github.nighter.smartspawner.commands.list.gui.list.UserPreferenceCache;
import github.nighter.smartspawner.commands.list.gui.worldselection.WorldSelectionHolder;
import github.nighter.smartspawner.commands.list.gui.serverselection.ServerSelectionHolder;
import github.nighter.smartspawner.commands.list.gui.management.SpawnerManagementGUI;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.language.section.GuiLanguageSection;
import github.nighter.smartspawner.spawner.data.database.SpawnerDatabaseHandler;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.utils.ItemTooltipUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class ListSubCommand extends BaseSubCommand {
    private final SpawnerManager spawnerManager;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final UserPreferenceCache userPreferenceCache;
    private final SpawnerManagementGUI spawnerManagementGUI;
    private final NamespacedKey worldNameKey;
    /** The server a server-selection button stands for; its name is display text and may say anything. */
    @lombok.Getter
    private final NamespacedKey serverNameKey;
    private static final int SPAWNERS_PER_PAGE = 45;

    public ListSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.userPreferenceCache = plugin.getUserPreferenceCache();
        this.spawnerManagementGUI = new SpawnerManagementGUI(plugin);
        this.worldNameKey = new NamespacedKey(plugin, "world_name");
        this.serverNameKey = new NamespacedKey(plugin, "server_name");
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.list";
    }

    @Override
    public String getDescription() {
        return "Open the spawner list GUI";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        if (!isPlayer(context.getSource().getSender())) {
            return 0;
        }

        Player player = getPlayer(context.getSource().getSender());

        // Check if cross-server mode is enabled
        if (isCrossServerEnabled()) {
            openServerSelectionGUI(player);
        } else {
            openWorldSelectionGUI(player);
        }
        return 1;
    }

    /**
     * Check if cross-server sync is enabled.
     * Requires MYSQL mode AND sync_across_servers = true
     * (SQLite is local-only and does not support cross-server sync)
     */
    public boolean isCrossServerEnabled() {
        StorageMode mode = StorageMode.fromConfig(plugin.getConfig().getString("database.type"));
        if (mode != StorageMode.MYSQL) {
            return false;
        }
        return plugin.getConfig().getBoolean("database.sync-across-servers", false);
    }

    /**
     * Get the current server name from config.
     */
    public String getCurrentServerName() {
        return plugin.getConfig().getString("database.server-name", "server1");
    }

    /**
     * Open the server selection GUI (async database query).
     */
    public void openServerSelectionGUI(Player player) {
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "command.no_permission");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (!isCrossServerEnabled() || dbHandler == null) {
            openWorldSelectionGUI(player);
            return;
        }

        // Async query for server names
        dbHandler.getDistinctServerNamesAsync(servers -> openServerSelectionGUI(player, servers));
    }

    private void openServerSelectionGUI(Player player, Collection<String> servers) {
        GuiLanguageSection gui = languageManager.commandGui(player);
        Set<String> availableServers = new LinkedHashSet<>();
        availableServers.add(getCurrentServerName());
        availableServers.addAll(servers);

        int contentRows = (int) Math.ceil(availableServers.size() / 7.0);
        int size = Math.max(27, (contentRows + 1) * 9);
        size = Math.min(54, size);

        Component title = gui.title("list.servers.title");
        Inventory inv = Bukkit.createInventory(new ServerSelectionHolder(), size, title);

        String currentServer = getCurrentServerName();
        int slot = 10;

        for (String serverName : availableServers) {
            if (slot >= size) break;

            // Skip border slots for nicer layout
            while (slot < size && (slot % 9 == 0 || slot % 9 == 8)) {
                slot++;
            }
            if (slot >= size) break;

            Material material = serverName.equals(currentServer) ? Material.EMERALD_BLOCK : Material.IRON_BLOCK;
            ItemStack serverItem = createServerButton(gui, serverName, material, serverName.equals(currentServer));
            inv.setItem(slot, serverItem);
            slot++;
        }

        player.openInventory(inv);
    }

    private ItemStack createServerButton(GuiLanguageSection gui, String serverName, Material material, boolean isCurrentServer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = Map.of("server", serverName);
            String key = isCurrentServer ? "list.servers.current" : "list.servers.remote";
            meta.displayName(gui.name(key + ".name", placeholders));
            meta.lore(gui.lore(key + ".lore", placeholders));
            meta.getPersistentDataContainer().set(serverNameKey, PersistentDataType.STRING, serverName);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Open world selection for a specific server (async for remote servers).
     */
    public void openWorldSelectionGUIForServer(Player player, String targetServer) {
        GuiLanguageSection gui = languageManager.commandGui(player);
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "command.no_permission");
            return;
        }

        String currentServer = getCurrentServerName();

        // If it's the current server, use local data
        if (targetServer.equals(currentServer)) {
            openWorldSelectionGUI(player);
            return;
        }

        // For remote servers, query async
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "general.action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        dbHandler.getWorldsForServerAsync(targetServer, worldCounts -> {
            if (worldCounts.isEmpty()) {
                messageService.sendMessage(player, "command.list.no_spawners_found");
                return;
            }

            int size = Math.max(27, (int) Math.ceil((worldCounts.size() + 2) / 7.0) * 9);
            size = Math.min(54, size);

            Map<String, String> titlePlaceholders = new HashMap<>();
            titlePlaceholders.put("server", targetServer);
            Component title = gui.title("list.worlds.title_server", titlePlaceholders);

            Inventory inv = Bukkit.createInventory(
                    new WorldSelectionHolder(targetServer),
                    size, title
            );

            int slot = 10;
            for (Map.Entry<String, SpawnerDatabaseHandler.WorldSpawnerStats> entry : worldCounts.entrySet()) {
                if (slot >= size - 9) break;

                // Skip border slots
                if (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                    continue;
                }

                String worldName = entry.getKey();
                SpawnerDatabaseHandler.WorldSpawnerStats stats = entry.getValue();
                World.Environment environment = getEnvironmentForWorldName(worldName);
                ItemStack worldItem = createWorldButton(
                        gui,
                        worldName,
                        environment,
                        stats.total(),
                        stats.totalStacked(),
                        targetServer
                );
                inv.setItem(slot, worldItem);
                slot++;
            }

            // Back button
            ItemStack backButton = createNavigationButton(
                    gui,
                    Material.RED_STAINED_GLASS_PANE,
                    "navigation.back",
                    previousMenuPlaceholder(gui, "server_selection", "Server Selection")
            );
            inv.setItem(26, backButton);

            player.openInventory(inv);
        });
    }

    private World.Environment getEnvironmentForWorldName(String worldName) {
        String normalizedName = worldName.toLowerCase(Locale.ROOT);
        if (normalizedName.equals("world_nether") || normalizedName.endsWith("_nether")) {
            return World.Environment.NETHER;
        }
        if (normalizedName.equals("world_the_end") || normalizedName.endsWith("_the_end")) {
            return World.Environment.THE_END;
        }
        return World.Environment.NORMAL;
    }

    private SpawnerDatabaseHandler getDbHandler() {
        if (plugin.getSpawnerStorage() instanceof SpawnerDatabaseHandler dbHandler) {
            return dbHandler;
        }
        return null;
    }

    // World selection GUI logic (unchanged)
    public void openWorldSelectionGUI(Player player) {
        openWorldSelectionGUI(player, null);
    }

    private void openWorldSelectionGUI(Player player, String targetServer) {
        GuiLanguageSection gui = languageManager.commandGui(player);
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "command.no_permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Get all loaded worlds with spawners
        List<World> worlds = Bukkit.getWorlds().stream()
                .filter(world -> spawnerManager.countSpawnersInWorld(world.getName()) > 0)
                .collect(Collectors.toList());

        // Check if there are any custom worlds with spawners
        List<World> customWorlds = worlds.stream()
                .filter(world -> !isDefaultWorld(world.getName()))
                .collect(Collectors.toList());

        boolean hasCustomWorlds = !customWorlds.isEmpty();

        // Calculate inventory size - use original 27 size if only default worlds, otherwise adapt
        int size = hasCustomWorlds ? Math.max(27, (int) Math.ceil((worlds.size() + 2) / 7.0) * 9) : 27;

        Inventory inv = Bukkit.createInventory(new WorldSelectionHolder(targetServer),
                size, gui.title("list.worlds.title"));

        // If we only have default worlds, use the original layout
        if (!hasCustomWorlds) {
            // Create buttons for default worlds
            ItemStack overworldButton = createWorldButtonIfWorldExists(gui, "world", targetServer);
            ItemStack netherButton = createWorldButtonIfWorldExists(gui, "world_nether", targetServer);
            ItemStack endButton = createWorldButtonIfWorldExists(gui, "world_the_end", targetServer);

            // Set buttons in the original layout
            if (overworldButton != null) inv.setItem(11, overworldButton);
            if (netherButton != null) inv.setItem(13, netherButton);
            if (endButton != null) inv.setItem(15, endButton);
        }
        // If we have custom worlds, use a more flexible layout
        else {
            int slot = 10; // Start at second row, second column
            int row = 1;

            // Add default worlds first (if they exist)
            if (addWorldButtonIfExists(gui, inv, "world", slot, targetServer)) {
                slot++;
            }

            if (addWorldButtonIfExists(gui, inv, "world_nether", slot, targetServer)) {
                slot++;
            }

            if (addWorldButtonIfExists(gui, inv, "world_the_end", slot, targetServer)) {
                slot++;
            }

            // Add custom worlds
            for (World world : customWorlds) {
                // Move to next row if we've reached the end of this one
                if (slot % 9 == 8) {
                    row++;
                    slot = 9 * row + 1; // First slot in the next row (skipping the border)
                }

                // Stop if we've filled the inventory
                if (slot >= size) {
                    break;
                }

                // Add the world button
                addWorldButton(gui, inv, world, slot++, targetServer);
            }
        }

        // Add back button if cross-server mode is enabled
        if (isCrossServerEnabled() || targetServer != null) {
            ItemStack backButton = createNavigationButton(
                    gui,
                    Material.RED_STAINED_GLASS_PANE,
                    "navigation.back",
                    previousMenuPlaceholder(gui, "server_selection", "Server Selection")
            );
            inv.setItem(26, backButton);
        }

        player.openInventory(inv);
    }

    private boolean isDefaultWorld(String worldName) {
        return worldName.equals("world") || worldName.equals("world_nether") || worldName.equals("world_the_end");
    }

    private ItemStack createWorldButtonIfWorldExists(GuiLanguageSection gui, String worldName, String serverName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null && spawnerManager.countSpawnersInWorld(worldName) > 0) {
            return createWorldButton(gui, world, serverName);
        }
        return null;
    }

    private boolean addWorldButtonIfExists(
            GuiLanguageSection gui,
            Inventory inv,
            String worldName,
            int slot,
            String serverName
    ) {
        World world = Bukkit.getWorld(worldName);
        if (world != null && spawnerManager.countSpawnersInWorld(worldName) > 0) {
            addWorldButton(gui, inv, world, slot, serverName);
            return true;
        }
        return false;
    }

    private void addWorldButton(GuiLanguageSection gui, Inventory inv, World world, int slot, String serverName) {
        inv.setItem(slot, createWorldButton(gui, world, serverName));
    }

    private Material getMaterialForWorldType(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> Material.GRASS_BLOCK;
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            case CUSTOM -> Material.ENDER_PEARL;
        };
    }

    private String formatWorldName(String worldName) {
        // Convert something like "my_custom_world" to "My Custom World"
        return Arrays.stream(worldName.replace('_', ' ').split(" "))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String getWorldDisplayName(GuiLanguageSection gui, String worldName) {
        String configKey;
        String defaultValue;
        switch (worldName) {
            case "world" -> {
                configKey = "list.worlds.names.overworld";
                defaultValue = "Overworld";
            }
            case "world_nether" -> {
                configKey = "list.worlds.names.nether";
                defaultValue = "Nether";
            }
            case "world_the_end" -> {
                configKey = "list.worlds.names.the_end";
                defaultValue = "The End";
            }
            default -> {
                String customFormat = gui.configString(
                        "list.worlds.names.custom",
                        "World {world_name}"
                );
                return languageManager.applyOnlyPlaceholders(
                        customFormat,
                        Map.of("world_name", formatWorldName(worldName))
                );
            }
        }
        return gui.configString(configKey, defaultValue);
    }

    private ItemStack createWorldButton(GuiLanguageSection gui, World world, String serverName) {
        String worldName = world.getName();
        int physicalSpawners = spawnerManager.countSpawnersInWorld(worldName);
        int totalWithStacks = spawnerManager.countTotalSpawnersWithStacks(worldName);
        return createWorldButton(gui, worldName, world.getEnvironment(), physicalSpawners, totalWithStacks, serverName);
    }

    private ItemStack createWorldButton(
            GuiLanguageSection gui,
            String worldName,
            World.Environment environment,
            int physicalSpawners,
            int totalWithStacks,
            String serverName
    ) {
        String path = "list.worlds.buttons." + environment.name();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world_name", getWorldDisplayName(gui, worldName));
        placeholders.put("total", languageManager.formatNumber(physicalSpawners));
        placeholders.put("total_stacked", languageManager.formatNumber(totalWithStacks));
        // {remote_world_lore} is a block of lines on a remote world, and no line at all on this server.
        List<Component> remoteLore = List.of();
        if (serverName != null) {
            placeholders.put("server", serverName);
            remoteLore = gui.lore("list.worlds.remote_lore", placeholders);
        }

        ItemStack button = new ItemStack(getMaterialForWorldType(environment));
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(path + ".name", placeholders));
            meta.lore(gui.lore(path + ".lore", placeholders, Map.of("remote_world_lore", remoteLore)));
            meta.getPersistentDataContainer().set(worldNameKey, PersistentDataType.STRING, worldName);
            button.setItemMeta(meta);
        }
        return button;
    }

    // Basic spawner list GUI opener with default filter and sort
    public void openSpawnerListGUI(Player player, String worldName, int page) {
        // Check for saved preferences
        UserPreferenceCache.UserPreference preference = userPreferenceCache.getPreference(player.getUniqueId(), worldName);

        if (preference != null) {
            // Use saved preferences if available
            openSpawnerListGUI(player, worldName, page, preference.getFilterOption(), preference.getSortOption());
        } else {
            // Use default preferences
            openSpawnerListGUI(player, worldName, page, FilterOption.ALL, SortOption.DEFAULT);
        }
    }

    public void saveUserPreference(Player player, String worldName, FilterOption filter, SortOption sort) {
        userPreferenceCache.savePreference(player.getUniqueId(), worldName, filter, sort);
    }

    // Main spawner list GUI method with filter and sort options
    public void openSpawnerListGUI(Player player, String worldName, int page, FilterOption filter, SortOption sortType) {
        openSpawnerListGUI(player, worldName, page, filter, sortType, null);
    }

    private void openSpawnerListGUI(
            Player player,
            String worldName,
            int page,
            FilterOption filter,
            SortOption sortType,
            String targetServer
    ) {
        GuiLanguageSection gui = languageManager.commandGui(player);
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "command.no_permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Get all spawners in the world
        List<SpawnerData> worldSpawners = spawnerManager.getAllSpawners().stream()
                .filter(spawner -> spawner.getSpawnerLocation().getWorld().getName().equals(worldName))
                .collect(Collectors.toList());

        // Apply filtering
        if (filter == FilterOption.ACTIVE) {
            worldSpawners = worldSpawners.stream()
                    .filter(spawner -> !spawner.getSpawnerStop().get())
                    .collect(Collectors.toList());
        } else if (filter == FilterOption.INACTIVE) {
            worldSpawners = worldSpawners.stream()
                    .filter(spawner -> spawner.getSpawnerStop().get())
                    .collect(Collectors.toList());
        }

        // Apply sorting
        switch (sortType) {
            case STACK_SIZE_ASC -> worldSpawners.sort(Comparator.comparingInt(SpawnerData::getStackSize));
            case STACK_SIZE_DESC -> worldSpawners.sort(Comparator.comparingInt(SpawnerData::getStackSize).reversed());
            default -> {} // Default sorting (by ID) - no additional sorting needed
        }

        int totalPages = (int) Math.ceil((double) worldSpawners.size() / SPAWNERS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("world", getWorldDisplayName(gui, worldName));
        titlePlaceholders.put("current", String.valueOf(page));
        titlePlaceholders.put("total", String.valueOf(totalPages));

        String titleKey = targetServer == null
                ? "list.spawners.title"
                : "list.spawners.title_remote";
        if (targetServer != null) {
            titlePlaceholders.put("server", targetServer);
        }
        Component title = gui.title(titleKey, titlePlaceholders);

        Inventory inv = Bukkit.createInventory(
                new SpawnerListHolder(page, totalPages, worldName, filter, sortType, targetServer),
                54, title);

        // Calculate start and end indices for current page
        int startIndex = (page - 1) * SPAWNERS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPAWNERS_PER_PAGE, worldSpawners.size());

        // Populate inventory with spawners
        for (int i = startIndex; i < endIndex; i++) {
            SpawnerData spawner = worldSpawners.get(i);
            inv.addItem(createSpawnerInfoItem(gui, spawner, targetServer != null));
        }

        // Add filter and sort controls
        addControlButtons(gui, inv, filter, sortType);

        // Add navigation buttons
        if (page > 1) {
            inv.setItem(45, createNavigationButton(
                    gui,
                    Material.SPECTRAL_ARROW,
                    "navigation.previous_page",
                    Map.of("target_page", String.valueOf(page - 1))));
        }

        // Back button
        inv.setItem(49, createNavigationButton(
                gui,
                Material.RED_STAINED_GLASS_PANE,
                "navigation.back",
                previousMenuPlaceholder(gui, "world_selection", "World Selection")
        ));

        if (page < totalPages) {
            inv.setItem(53, createNavigationButton(
                    gui,
                    Material.SPECTRAL_ARROW,
                    "navigation.next_page",
                    Map.of("target_page", String.valueOf(page + 1))));
        }

        player.openInventory(inv);
    }

    // Create the new consolidated filter and sort buttons
    private void addControlButtons(GuiLanguageSection gui, Inventory inv, FilterOption currentFilter, SortOption currentSort) {
        // Filter button (updated material and position) - moved to slot 46
        ItemStack filterButton = createEnhancedControlButton(
                gui,
                Material.CAULDRON,
                "filter",
                currentFilter
        );

        // Sort button (updated material and position) - moved to slot 52
        ItemStack sortButton = createEnhancedControlButton(
                gui,
                Material.HOPPER,
                "sort",
                currentSort
        );

        // Updated positions for better symmetry
        inv.setItem(48, filterButton);
        inv.setItem(50, sortButton);
    }

    private ItemStack createEnhancedControlButton(GuiLanguageSection gui, Material material, String controlType, Enum<?> currentOption) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta == null) return button;

        // One lore line per option, from the selected or unselected template.
        List<Component> availableOptions = new ArrayList<>();
        Enum<?>[] options = controlType.equals("filter") ? FilterOption.values() : SortOption.values();
        for (Enum<?> option : options) {
            String name = option instanceof FilterOption filter ? filter.getName() : ((SortOption) option).getName();
            String optionName = gui.configString("list.spawners." + controlType + "_options." + name, name);
            String format = option == currentOption ? "list.spawners.selected_option" : "list.spawners.unselected_option";
            availableOptions.add(gui.name(format, Map.of("option_name", optionName)));
        }

        String buttonPath = "list.spawners." + controlType;
        meta.displayName(gui.name(buttonPath + ".name"));
        meta.lore(gui.lore(buttonPath + ".lore", Map.of(), Map.of("available_options", availableOptions)));

        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createNavigationButton(GuiLanguageSection gui, Material material, String namePath) {
        return createNavigationButton(gui, material, namePath, Collections.emptyMap());
    }

    private ItemStack createNavigationButton(GuiLanguageSection gui, Material material, String namePath, Map<String, String> placeholders) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            // A button is a section with name and lore; a plain text works as a name too.
            meta.displayName(gui.raw(namePath + ".name").isEmpty() && !gui.raw(namePath).isEmpty()
                    ? gui.name(namePath, placeholders)
                    : gui.name(namePath + ".name", placeholders));
            List<Component> lore = gui.lore(namePath + ".lore", placeholders);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            button.setItemMeta(meta);
        }
        return button;
    }

    private Map<String, String> previousMenuPlaceholder(GuiLanguageSection gui, String menuKey, String defaultName) {
        String menuName = gui.configString(
                "navigation.menu_names." + menuKey,
                defaultName
        );
        return Map.of("previous_menu", menuName);
    }


    private ItemStack createSpawnerInfoItem(GuiLanguageSection gui, SpawnerData spawner, boolean remote) {
        EntityType entityType = spawner.getEntityType();
        Location loc = spawner.getSpawnerLocation();

        // Prepare all placeholders upfront
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", String.valueOf(spawner.getSpawnerId()));
        placeholders.put("entity", spawner.getDisplayName());
        placeholders.put("size", String.valueOf(spawner.getStackSize()));
        if (spawner.getSpawnerStop().get()) {
            placeholders.put("status", gui.configString("list.spawners.spawner.status.inactive", "Inactive"));
        } else {
            placeholders.put("status", gui.configString("list.spawners.spawner.status.active", "Active"));
        }
        placeholders.put("x", String.valueOf(loc.getBlockX()));
        placeholders.put("y", String.valueOf(loc.getBlockY()));
        placeholders.put("z", String.valueOf(loc.getBlockZ()));
        String lastPlayer = spawner.getLastInteractedPlayer();
        placeholders.put("last_player", lastPlayer != null
                ? lastPlayer
                : gui.configString("list.spawners.spawner.last_player_none", "None"));

        ItemStack spawnerItem;
        String loreKey = remote ? "list.spawners.spawner.remote_lore" : "list.spawners.spawner.lore";

        if (entityType == null) {
            spawnerItem = new ItemStack(Material.SPAWNER);
            spawnerItem.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.displayName(gui.name("list.spawners.spawner.name", placeholders));
                meta.lore(gui.lore(loreKey, placeholders));
            });
        } else {
            // Use optimized method with consumer to avoid extra getItemMeta/setItemMeta
            spawnerItem = SpawnerMobHeadTexture.getSpawnerHead(spawner, meta -> {
                meta.displayName(gui.name("list.spawners.spawner.name", placeholders));
                meta.lore(gui.lore(loreKey, placeholders));
            });
        }

        ItemTooltipUtil.hideTooltip(spawnerItem);
        return spawnerItem;
    }

    public void openSpawnerManagementGUI(Player player, String spawnerId, String worldName, int listPage) {
        spawnerManagementGUI.openManagementMenu(player, spawnerId, worldName, listPage);
    }

    /**
     * Open spawner list GUI for a remote server (async database query).
     */
    public void openSpawnerListGUIForServer(Player player, String targetServer, String worldName, int page) {
        // Use default filter and sort
        openSpawnerListGUIForServer(player, targetServer, worldName, page, FilterOption.ALL, SortOption.DEFAULT);
    }

    /**
     * Open spawner list GUI for a remote server with filter and sort options.
     */
    public void openSpawnerListGUIForServer(Player player, String targetServer, String worldName, int page,
                                            FilterOption filter, SortOption sort) {
        GuiLanguageSection gui = languageManager.commandGui(player);
        if (!player.hasPermission("smartspawner.command.list")) {
            messageService.sendMessage(player, "command.no_permission");
            return;
        }

        String currentServer = getCurrentServerName();

        // If it's the current server, use local data
        if (targetServer.equals(currentServer)) {
            openSpawnerListGUI(player, worldName, page, filter, sort, targetServer);
            return;
        }

        // For remote servers, query async
        SpawnerDatabaseHandler dbHandler = getDbHandler();
        if (dbHandler == null) {
            messageService.sendMessage(player, "general.action_failed");
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        final int requestedPage = page;
        final FilterOption finalFilter = filter;
        final SortOption finalSort = sort;
        dbHandler.getCrossServerSpawnersAsync(targetServer, worldName, filter.name(), sort.name(), spawners -> {
            if (spawners.isEmpty()) {
                messageService.sendMessage(player, "command.list.no_spawners_found");
                return;
            }

            int totalPages = (int) Math.ceil((double) spawners.size() / SPAWNERS_PER_PAGE);
            int currentPage = Math.max(1, Math.min(requestedPage, totalPages));

            String worldTitle = getWorldDisplayName(gui, worldName);

            Map<String, String> titlePlaceholders = new HashMap<>();
            titlePlaceholders.put("world", worldTitle);
            titlePlaceholders.put("current", String.valueOf(currentPage));
            titlePlaceholders.put("total", String.valueOf(totalPages));
            titlePlaceholders.put("server", targetServer);

            Component title = gui.title(
                    "list.spawners.title_remote",
                    titlePlaceholders
            );

            Inventory inv = Bukkit.createInventory(
                new SpawnerListHolder(currentPage, totalPages, worldName, finalFilter, finalSort, targetServer),
                54, title
            );

            // Calculate start and end indices for current page
            int startIndex = (currentPage - 1) * SPAWNERS_PER_PAGE;
            int endIndex = Math.min(startIndex + SPAWNERS_PER_PAGE, spawners.size());

            // Populate inventory with spawners
            for (int i = startIndex; i < endIndex; i++) {
                CrossServerSpawnerData spawner = spawners.get(i);
                inv.addItem(createCrossServerSpawnerItem(gui, spawner));
            }

            // Add navigation buttons
            // Previous page
            if (currentPage > 1) {
                inv.setItem(45, createNavigationButton(
                        gui,
                        Material.SPECTRAL_ARROW,
                        "navigation.previous_page",
                        Map.of("target_page", String.valueOf(currentPage - 1))));
            }

            // Filter button (slot 48)
            addControlButtons(gui, inv, finalFilter, finalSort);

            // Back button
            inv.setItem(49, createNavigationButton(
                    gui,
                    Material.RED_STAINED_GLASS_PANE,
                    "navigation.back",
                    previousMenuPlaceholder(gui, "world_selection", "World Selection")
            ));

            // Next page
            if (currentPage < totalPages) {
                inv.setItem(53, createNavigationButton(
                        gui,
                        Material.SPECTRAL_ARROW,
                        "navigation.next_page",
                        Map.of("target_page", String.valueOf(currentPage + 1))));
            }

            player.openInventory(inv);
        });
    }

    private ItemStack createCrossServerSpawnerItem(GuiLanguageSection gui, CrossServerSpawnerData spawner) {
        EntityType entityType = spawner.getEntityType();

        // Prepare all placeholders
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        placeholders.put("entity", languageManager.getFormattedMobName(entityType));
        placeholders.put("size", String.valueOf(spawner.getStackSize()));
        if (!spawner.isActive()) {
            placeholders.put("status", gui.configString("list.spawners.spawner.status.inactive", "Inactive"));
        } else {
            placeholders.put("status", gui.configString("list.spawners.spawner.status.active", "Active"));
        }
        placeholders.put("x", String.valueOf(spawner.getLocX()));
        placeholders.put("y", String.valueOf(spawner.getLocY()));
        placeholders.put("z", String.valueOf(spawner.getLocZ()));
        String lastPlayer = spawner.getLastInteractedPlayer();
        placeholders.put("last_player", lastPlayer != null
                ? lastPlayer
                : gui.configString("list.spawners.spawner.last_player_none", "None"));

        ItemStack spawnerItem;

        if (entityType == null) {
            spawnerItem = new ItemStack(Material.SPAWNER);
            spawnerItem.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
                meta.displayName(gui.name("list.spawners.spawner.name", placeholders));
                meta.lore(gui.lore("list.spawners.spawner.remote_lore", placeholders));
            });
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, meta -> {
                meta.displayName(gui.name("list.spawners.spawner.name", placeholders));
                meta.lore(gui.lore("list.spawners.spawner.remote_lore", placeholders));
            });
        }

        ItemTooltipUtil.hideTooltip(spawnerItem);
        return spawnerItem;
    }
    public FilterOption getUserFilter(Player player, String worldName) {
        return userPreferenceCache.getUserFilter(player, worldName);
    }

    /**
     * Gets the user's current sort preference for a world
     */
    public SortOption getUserSort(Player player, String worldName) {
        return userPreferenceCache.getUserSort(player, worldName);
    }
}
