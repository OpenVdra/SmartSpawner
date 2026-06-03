package github.nighter.smartspawner.hooks.economy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.currency.CurrencyManager;
import github.nighter.smartspawner.hooks.economy.shops.ShopIntegrationManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ItemPriceManager {
    private static final String PRICE_FILE_NAME = "item_prices.yml";

    private final SmartSpawner plugin;
    private final Map<String, Double> itemPrices = new ConcurrentHashMap<>();
    private File priceFile;
    private FileConfiguration priceConfig;

    @Getter
    private ShopIntegrationManager shopIntegrationManager;
    @Getter
    private CurrencyManager currencyManager;

    private double defaultPrice;
    private PriceSourceMode priceSourceMode;
    private boolean economyEnabled;
    public boolean customPricesEnabled;
    public boolean shopIntegrationEnabled;

    public enum PriceSourceMode {
        CUSTOM_ONLY,
        SHOP_ONLY,
        CUSTOM_PRIORITY,
        SHOP_PRIORITY
    }

    public void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        loadConfiguration(); // Load configuration first to get the file name

        priceFile = new File(plugin.getDataFolder(), PRICE_FILE_NAME);

        if (!priceFile.exists()) {
            plugin.saveResource(PRICE_FILE_NAME, false);
        }

        priceConfig = YamlConfiguration.loadConfiguration(priceFile);

        // Only initialize components if economy is enabled
        if (economyEnabled) {
            // Initialize currency manager
            currencyManager = new CurrencyManager(plugin);
            currencyManager.initialize();

            // Initialize shop integration if enabled
            if (shopIntegrationEnabled) {
                shopIntegrationManager = new ShopIntegrationManager(plugin);
                shopIntegrationManager.initialize();
            }

            // Load custom prices if enabled
            if (customPricesEnabled) {
                loadPrices();
            }

            // Validate price source mode configuration
            validatePriceSourceMode();
        } else {
            plugin.getLogger().info("Sell integration is disabled. No sell integration will be available.");
        }
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        this.economyEnabled = config.getBoolean("sell_integration.enabled", true);
        this.defaultPrice = config.getDouble("sell_integration.custom_prices.default_price", 1.0);
        this.customPricesEnabled = config.getBoolean("sell_integration.custom_prices.enabled", true);
        this.shopIntegrationEnabled = config.getBoolean("sell_integration.shop_integration.enabled", true);

        String modeString = config.getString("sell_integration.price_source_mode", "SHOP_PRIORITY");
        try {
            this.priceSourceMode = PriceSourceMode.valueOf(modeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid price source mode: " + modeString + ". Using SHOP_PRIORITY");
            this.priceSourceMode = PriceSourceMode.SHOP_PRIORITY;
        }
    }

    private void validatePriceSourceMode() {
        if (!economyEnabled) {
            return; // Skip validation if economy is disabled
        }

        boolean hasValidShopIntegration = shopIntegrationEnabled && shopIntegrationManager != null && shopIntegrationManager.hasActiveProvider();
        boolean hasValidCustomPrices = this.customPricesEnabled && !itemPrices.isEmpty();

        // If price source mode is CUSTOM_ONLY but shop integration is enabled and working,
        if (priceSourceMode == PriceSourceMode.CUSTOM_ONLY && hasValidShopIntegration) {
            plugin.getLogger().warning("Price source mode is set to CUSTOM_ONLY but shop integration is enabled and working.");
            plugin.getLogger().warning("Prices from shop integration will not be used.");
        }

        // If price source mode is SHOP_ONLY but no valid shop integration
        if (priceSourceMode == PriceSourceMode.SHOP_ONLY && !hasValidShopIntegration) {
            plugin.getLogger().warning("Price source mode is set to SHOP_ONLY but no valid shop integration is available.");
            plugin.getLogger().warning("Selling items from spawner will be disabled.");
        }

        // Validate priority modes - ensure at least one source is valid
        if (priceSourceMode == PriceSourceMode.CUSTOM_PRIORITY || priceSourceMode == PriceSourceMode.SHOP_PRIORITY) {
            if (!hasValidCustomPrices && !hasValidShopIntegration) {
                plugin.getLogger().warning("Price source mode " + priceSourceMode + " requires at least one valid price source (custom or shop).");
                plugin.getLogger().warning("Selling items from spawner will be disabled.");
            }
        }

        // Additional validation for CUSTOM_ONLY mode
        if (priceSourceMode == PriceSourceMode.CUSTOM_ONLY && !hasValidCustomPrices) {
            plugin.getLogger().warning("Price source mode is set to CUSTOM_ONLY but no valid custom prices are available.");
            plugin.getLogger().warning("Custom prices enabled: " + this.customPricesEnabled + ", Loaded prices: " + itemPrices.size());
            plugin.getLogger().warning("Selling items from spawner will be disabled.");
        }
    }

    private void loadPrices() {
        itemPrices.clear();
        for (String key : priceConfig.getKeys(false)) {
            double price = priceConfig.getDouble(key, defaultPrice);
            itemPrices.put(key, price);
        }
    }

    public double getPrice(Material material) {
        if (material == null || !economyEnabled) return 0.0;

        switch (priceSourceMode) {
            case CUSTOM_ONLY:
                return getCustomPrice(material);
            case SHOP_ONLY:
                return getShopPrice(material);
            case CUSTOM_PRIORITY:
                double customPrice = getCustomPrice(material);
                return customPrice > 0 ? customPrice : getShopPrice(material);
            case SHOP_PRIORITY:
                double shopPrice = getShopPrice(material);
                return shopPrice > 0 ? shopPrice : getCustomPrice(material);
            default:
                return defaultPrice;
        }
    }

    private double getCustomPrice(Material material) {
        if (!economyEnabled || !customPricesEnabled) return 0.0;
        return itemPrices.getOrDefault(material.name(), defaultPrice);
    }

    private double getShopPrice(Material material) {
        if (!economyEnabled || !shopIntegrationEnabled || shopIntegrationManager == null) return 0.0;
        return shopIntegrationManager.getPrice(material);
    }

    public void setPrice(Material material, double price) {
        if (material == null || !economyEnabled || !customPricesEnabled) return;

        itemPrices.put(material.name(), price);
        priceConfig.set(material.name(), price);
        saveConfig();
    }

    public void reload() {
        loadConfiguration();

        // Only reload components if economy is enabled
        if (economyEnabled) {
            // Update price file path in case it changed
            priceFile = new File(plugin.getDataFolder(), PRICE_FILE_NAME);

            // Reload currency manager
            if (currencyManager != null) {
                currencyManager.reload();
            } else {
                currencyManager = new CurrencyManager(plugin);
                currencyManager.initialize();
            }

            // Reload shop integration
            if (shopIntegrationEnabled) {
                if (shopIntegrationManager == null) {
                    shopIntegrationManager = new ShopIntegrationManager(plugin);
                }
                shopIntegrationManager.initialize();
            } else {
                shopIntegrationManager = null;
            }

            // Reload custom prices
            if (customPricesEnabled) {
                priceConfig = YamlConfiguration.loadConfiguration(priceFile);
                loadPrices();
            } else {
                itemPrices.clear();
            }

            // Validate configuration after reload
            validatePriceSourceMode();
        } else {
            // Clean up if economy is disabled
            if (currencyManager != null) {
                currencyManager.cleanup();
                currencyManager = null;
            }
            shopIntegrationManager = null;
            itemPrices.clear();
            plugin.getLogger().info("Storage selling disabled - all sell integration cleaned up.");
        }
    }

    public void reloadShopIntegration() {
        if (shopIntegrationEnabled) {
            if (shopIntegrationManager == null) {
                shopIntegrationManager = new ShopIntegrationManager(plugin);
            }
            shopIntegrationManager.initialize();
        } else {
            shopIntegrationManager = null;
        }
    }

    public boolean hasSellIntegration() {
        // If economy is globally disabled, always return false
        if (!economyEnabled) {
            return false;
        }

        // Currency must be available for any selling functionality
        if (currencyManager == null || !currencyManager.isCurrencyAvailable()) {
            return false;
        }

        // At least one price source must be enabled and functional
        boolean hasValidCustomPrices = customPricesEnabled && !itemPrices.isEmpty();
        boolean hasValidShopIntegration = shopIntegrationEnabled && shopIntegrationManager != null && shopIntegrationManager.hasActiveProvider();

        // For CUSTOM_ONLY mode, only check custom prices (ignore shop integration status)
        if (priceSourceMode == PriceSourceMode.CUSTOM_ONLY) {
            return hasValidCustomPrices;
        }

        // For SHOP_ONLY mode, only check shop integration
        if (priceSourceMode == PriceSourceMode.SHOP_ONLY) {
            return hasValidShopIntegration;
        }

        // For priority modes, at least one should be available
        return hasValidCustomPrices || hasValidShopIntegration;
    }

    public boolean hasPriceFor(Material material) {
        if (material == null || !economyEnabled) return false;

        return switch (priceSourceMode) {
            case CUSTOM_ONLY -> customPricesEnabled && itemPrices.containsKey(material.name());
            case SHOP_ONLY -> shopIntegrationEnabled && shopIntegrationManager != null &&
                    shopIntegrationManager.getPrice(material) > 0;
            case CUSTOM_PRIORITY, SHOP_PRIORITY -> (customPricesEnabled && itemPrices.containsKey(material.name())) ||
                    (shopIntegrationEnabled && shopIntegrationManager != null &&
                            shopIntegrationManager.getPrice(material) > 0);
            default -> false;
        };
    }

    public void removePrice(Material material) {
        if (material == null || !economyEnabled || !customPricesEnabled) return;

        itemPrices.remove(material.name());
        priceConfig.set(material.name(), null);
        saveConfig();
    }

    public Map<String, Double> getAllPrices() {
        if (!economyEnabled) {
            return new ConcurrentHashMap<>();
        }
        return new ConcurrentHashMap<>(itemPrices);
    }

    public String getActivePriceSource() {
        if (!economyEnabled) {
            return "Economy Disabled";
        }

        StringBuilder sources = new StringBuilder();

        if (!customPricesEnabled && !shopIntegrationEnabled) {
            sources.append("None (using default prices)");
        } else {
            if (customPricesEnabled) sources.append("Custom");
            if (shopIntegrationEnabled) {
                if (sources.length() > 0) sources.append(" + ");
                String activeShop = shopIntegrationManager != null ? shopIntegrationManager.getActiveShopPlugin() : "None";
                sources.append("Shop (").append(activeShop).append(")");
            }
        }

        sources.append(" [Mode: ").append(priceSourceMode).append("]");

        // Add currency information
        if (currencyManager != null) {
            sources.append(" [Currency: ").append(currencyManager.getActiveCurrencyProvider()).append("]");
        }

        return sources.toString();
    }

    public void debugPricesForMaterials(Set<Material> materials) {
        plugin.debug("=== Item Prices Debug Info ===");
        plugin.debug("Economy Enabled: " + economyEnabled);
        plugin.debug("Mode: " + priceSourceMode);
        plugin.debug("Custom Prices Enabled: " + customPricesEnabled);
        plugin.debug("Shop Integration Enabled: " + shopIntegrationEnabled);
        plugin.debug("Default Price: " + defaultPrice);
        plugin.debug("Active Price Sources: " + getActivePriceSource());
        plugin.debug("Sell Integration Available: " + hasSellIntegration());

        if (!economyEnabled) {
            plugin.debug("Economy is disabled - skipping detailed price debug");
            return;
        }

        plugin.debug("Loaded " + materials.size() + " loot items with prices:");
        for (Material material : materials) {
            double finalPrice = getPrice(material);
            double customPrice = getCustomPrice(material);
            double shopPrice = getShopPrice(material);

            StringBuilder debug = new StringBuilder();
            debug.append("  ").append(material.name()).append(": Final=").append(String.format("%.2f", finalPrice));

            debug.append(" [");
            if (customPricesEnabled) {
                debug.append("Custom=").append(String.format("%.2f", customPrice));
            }
            if (shopIntegrationEnabled) {
                if (customPricesEnabled) debug.append(", ");
                debug.append("Shop=").append(String.format("%.2f", shopPrice));
            }
            debug.append("]");

            String source = determineActiveSource(customPrice, shopPrice);
            debug.append(" <- ").append(source);

            plugin.debug(debug.toString());
        }
    }

    private String determineActiveSource(double customPrice, double shopPrice) {
        if (!economyEnabled) return "Disabled";

        return switch (priceSourceMode) {
            case CUSTOM_ONLY -> "Custom";
            case SHOP_ONLY -> "Shop";
            case CUSTOM_PRIORITY -> (customPrice > 0) ? "Custom" : "Shop";
            case SHOP_PRIORITY -> (shopPrice > 0) ? "Shop" : "Custom";
            default -> "Default";
        };
    }

    private void saveConfig() {
        if (!economyEnabled) return;

        try {
            priceConfig.save(priceFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save item prices configuration", e);
        }
    }

    public void cleanup() {
        if (currencyManager != null) {
            currencyManager.cleanup();
            currencyManager = null;
        }
        if (shopIntegrationManager != null) {
            shopIntegrationManager.cleanup();
            shopIntegrationManager = null;
        }
        itemPrices.clear();
    }
}
