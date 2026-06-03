package github.nighter.smartspawner.hooks.economy.shops;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui.EconomyShopGUIProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui.ESGUICompatibilityHandler;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.ShopGuiPlusProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus.SpawnerHook;
import github.nighter.smartspawner.hooks.economy.shops.providers.excellentshop.ExcellentShopProvider;
import github.nighter.smartspawner.hooks.economy.shops.providers.zshop.ZShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private ShopProvider activeProvider;
    private final List<ShopProvider> availableProviders = new ArrayList<>();
    private SpawnerHook spawnerHook = null;
    private ESGUICompatibilityHandler esguiCompatibilityHandler = null;

    public void initialize() {
        availableProviders.clear();
        activeProvider = null;

        detectAndRegisterActiveProviders();
        selectActiveProvider();
    }

    private void detectAndRegisterActiveProviders() {
        // Check configuration for preferred plugin first
        String configuredShop = plugin.getConfig().getString("sell_integration.shop_integration.preferred_plugin", "auto");
        boolean autoDetect = "auto".equalsIgnoreCase(configuredShop);

        // If a specific shop is configured, only try to load that one
        if (!autoDetect) {
            if (tryRegisterSpecificProvider(configuredShop)) {
                // plugin.getLogger().info("Successfully loaded configured shop plugin: " + configuredShop);
                return; // Stop here - we found and loaded the preferred plugin
            } else {
                plugin.getLogger().warning("Configured shop plugin '" + configuredShop + "' could not be loaded. Falling back to auto-detection.");
            }
        }

        registerProviderIfAvailable("EconomyShopGUI", () -> {
            EconomyShopGUIProvider provider = new EconomyShopGUIProvider(plugin);

            // Initialize PluginCompatibilityHandler after creating the provider and only if it's null
            if (provider.isAvailable() && esguiCompatibilityHandler == null) {
                try {
                    esguiCompatibilityHandler = new ESGUICompatibilityHandler(plugin);
                    plugin.getServer().getPluginManager().registerEvents(esguiCompatibilityHandler, plugin);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to register ESGUICompatibilityHandler: " + e.getMessage());
                }
            }

            return provider;
        });


        // Only try ShopGUIPlus if the plugin is actually present and enabled
        if (isPluginAvailable("ShopGUIPlus")) {
            registerProviderIfAvailable("ShopGUIPlus", () -> {
                // Register the spawner hook event listener before creating the provider
                if (spawnerHook == null) {
                    try {
                        spawnerHook = new SpawnerHook(plugin);
                        plugin.getServer().getPluginManager().registerEvents(spawnerHook, plugin);
                    } catch (Exception e) {
                        plugin.debug("Failed to register SpawnerHook: " + e.getMessage());
                        throw e; // Re-throw to prevent provider registration
                    }
                }
                return new ShopGuiPlusProvider(plugin);
            });
        }

        // registerProviderIfAvailable("ZShop", () -> new ZShopProvider(plugin));

        if (isPluginAvailable("ExcellentShop")) {
            registerProviderIfAvailable("ExcellentShop", () -> new ExcellentShopProvider(plugin));
        }
    }

    private boolean tryRegisterSpecificProvider(String providerName) {
        try {
            switch (providerName.toLowerCase()) {
                case "economyshopgui":
                    if (isPluginAvailable("EconomyShopGUI")) {
                        registerProviderIfAvailable("EconomyShopGUI", () -> {
                            EconomyShopGUIProvider provider = new EconomyShopGUIProvider(plugin);

                            // Initialize PluginCompatibilityHandler after creating the provider and only if it's null
                            if (provider.isAvailable() && esguiCompatibilityHandler == null) {
                                try {
                                    esguiCompatibilityHandler = new ESGUICompatibilityHandler(plugin);
                                    plugin.getServer().getPluginManager().registerEvents(esguiCompatibilityHandler, plugin);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to register ESGUICompatibilityHandler: " + e.getMessage());
                                }
                            }

                            return provider;
                        });
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "shopguiplus":
                    if (isPluginAvailable("ShopGUIPlus")) {
                        registerProviderIfAvailable("ShopGUIPlus", () -> {
                            if (spawnerHook == null) {
                                spawnerHook = new SpawnerHook(plugin);
                                plugin.getServer().getPluginManager().registerEvents(spawnerHook, plugin);
                                plugin.debug("Registered SpawnerHook event listener for ShopGUIPlus");
                            }
                            return new ShopGuiPlusProvider(plugin);
                        });
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "zshop":
                    if (isPluginAvailable("ZShop")) {
                        registerProviderIfAvailable("ZShop", () -> new ZShopProvider(plugin));
                        return !availableProviders.isEmpty();
                    }
                    break;
                case "excellentshop":
                    if (isPluginAvailable("ExcellentShop")) {
                        registerProviderIfAvailable("ExcellentShop", () -> new ExcellentShopProvider(plugin));
                        return !availableProviders.isEmpty();
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.debug("Failed to load specific provider " + providerName + ": " + e.getMessage());
        }
        return false;
    }

    private boolean isPluginAvailable(String pluginName) {
        Plugin targetPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        return targetPlugin != null && targetPlugin.isEnabled();
    }

    private void registerProviderIfAvailable(String providerName, Supplier<ShopProvider> providerSupplier) {
        // If we already have an active provider and we're in single-provider mode, skip
        if (!availableProviders.isEmpty()) {
            plugin.debug("Skipping " + providerName + " registration - already have active provider: " +
                    availableProviders.getFirst().getPluginName());
            return;
        }

        try {
            ShopProvider provider = providerSupplier.get();
            if (provider.isAvailable()) {
                availableProviders.add(provider);
            }
        } catch (NoClassDefFoundError e) {
            plugin.debug("Shop provider " + providerName + " classes not found (plugin not installed): " + e.getMessage());
        } catch (Exception e) {
            plugin.debug("Failed to initialize shop provider " + providerName + ": " + e.getMessage());
        }
    }

    private void selectActiveProvider() {
        if (availableProviders.isEmpty()) {
            plugin.getLogger().info("No compatible shop plugins found. Shop integration is disabled.");
            return;
        }

        // Since we now ensure only one provider is registered, just use the first (and only) one
        activeProvider = availableProviders.getFirst();
        plugin.getLogger().info("Auto-detected & successfully hook into shop plugin: " + activeProvider.getPluginName());
    }

    public double getPrice(Material material) {
        if (activeProvider == null || material == null) {
            return 0.0;
        }

        try {
            return activeProvider.getSellPrice(material);
        } catch (Exception e) {
            plugin.debug("Error getting price for " + material + " from " + activeProvider.getPluginName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    public String getActiveShopPlugin() {
        return activeProvider != null ? activeProvider.getPluginName() : "None";
    }

    public boolean hasActiveProvider() {
        return activeProvider != null;
    }

    public void cleanup() {
        availableProviders.clear();
        activeProvider = null;
        if (spawnerHook != null) {
            spawnerHook.unregister();
            spawnerHook = null;
        }
        if (esguiCompatibilityHandler != null) {
            esguiCompatibilityHandler = null;
        }
    }
}
