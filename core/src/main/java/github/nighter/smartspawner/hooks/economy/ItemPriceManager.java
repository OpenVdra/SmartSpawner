package github.nighter.smartspawner.hooks.economy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.currency.CurrencyManager;
import github.nighter.smartspawner.hooks.economy.shops.ShopIntegrationManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
    /** Every sell setting, prices included, lives in this file since 1.8.0. */
    private static final String SELL_FILE_NAME = SellIntegrationConfigUpdater.FILE_NAME;

    /** Section holding one price per material name. */
    private static final String PRICES_SECTION = "custom_prices.prices";

    private final SmartSpawner plugin;
    private final Map<String, Double> itemPrices = new ConcurrentHashMap<>();
    private final Set<String> pluginReferenceKeys = ConcurrentHashMap.newKeySet();
    private File sellFile;

    /**
     * The loaded {@code sell_integration.yml}. Read it rather than {@code plugin.getConfig()} for
     * anything under this file; {@link CurrencyManager} and {@link ShopIntegrationManager} both do.
     */
    @Getter
    private FileConfiguration sellConfig;

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

        // SellIntegrationConfigUpdater has already created and migrated the file by this point.
        loadConfiguration();

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
        sellFile = new File(plugin.getDataFolder(), SELL_FILE_NAME);
        if (!sellFile.exists()) {
            plugin.saveResource(SELL_FILE_NAME, false);
        }
        sellConfig = YamlConfiguration.loadConfiguration(sellFile);
        FileConfiguration config = sellConfig;

        this.economyEnabled = config.getBoolean("enabled", true);
        this.defaultPrice = config.getDouble("custom_prices.default_price", 1.0);
        this.customPricesEnabled = config.getBoolean("custom_prices.enabled", true);
        this.shopIntegrationEnabled = config.getBoolean("shop_integration.enabled", true);

        String modeString = config.getString("price_source_mode", "SHOP_PRIORITY");
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
        pluginReferenceKeys.clear();
        ConfigurationSection prices = sellConfig.getConfigurationSection(PRICES_SECTION);
        if (prices == null) {
            return;
        }
        for (String key : prices.getKeys(false)) {
            if (prices.isConfigurationSection(key)) {
                plugin.getLogger().warning("Ignoring price entry '" + key + "' in " + SELL_FILE_NAME
                        + ": a price key cannot contain a dot.");
                continue;
            }
            itemPrices.put(key, prices.getDouble(key, defaultPrice));
            if (isPluginReference(key)) {
                pluginReferenceKeys.add(key);
            }
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

    /**
     * The price of a loot row, which names its item either by material or by a reference to another
     * plugin's item. A reference with its own entry in the price list is answered from there whatever
     * price_source_mode says: a shop plugin can only price a {@link Material}, so asking it would sell
     * the item for what its base material is worth.
     *
     * @param configuredItem the item: value of the loot row
     */
    public double getPrice(String configuredItem, Material material) {
        if (!economyEnabled) return 0.0;

        Double referencePrice = pluginReferencePrice(configuredItem);
        return referencePrice != null ? referencePrice : getPrice(material);
    }

    /** True when this loot value is priced by its own entry rather than by its material's. */
    public boolean hasPluginReferencePrice(String configuredItem) {
        return pluginReferencePrice(configuredItem) != null;
    }

    private Double pluginReferencePrice(String configuredItem) {
        if (!customPricesEnabled || configuredItem == null
                || !pluginReferenceKeys.contains(configuredItem)) {
            return null;
        }
        return itemPrices.get(configuredItem);
    }

    /**
     * Mirrors ConfiguredItemParser's order, so a value a material answers to is a material:
     * {@code minecraft:arrow} is priced as an arrow and not as an item owned by "minecraft".
     */
    private static boolean isPluginReference(String key) {
        return key.indexOf(':') >= 0 && Material.matchMaterial(key) == null;
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
        sellConfig.set(PRICES_SECTION + "." + material.name(), price);
        saveConfig();
    }

    public void reload() {
        loadConfiguration();

        // Only reload components if economy is enabled
        if (economyEnabled) {
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
            pluginReferenceKeys.clear();
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
        sellConfig.set(PRICES_SECTION + "." + material.name(), null);
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

    private void saveConfig() {
        if (!economyEnabled) return;

        try {
            sellConfig.save(sellFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + SELL_FILE_NAME, e);
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
        pluginReferenceKeys.clear();
    }
}
