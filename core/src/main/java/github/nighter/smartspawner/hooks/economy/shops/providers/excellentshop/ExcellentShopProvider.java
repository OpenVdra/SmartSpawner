package github.nighter.smartspawner.hooks.economy.shops.providers.excellentshop;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import su.nightexpress.excellentshop.ShopAPI;
import su.nightexpress.excellentshop.api.product.ContentType;
import su.nightexpress.excellentshop.api.product.Product;
import su.nightexpress.excellentshop.virtualshop.VirtualShopModule;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class ExcellentShopProvider implements ShopProvider {

    private final SmartSpawner plugin;

    // Cache: Material -> best sell price across all virtual shops
    private final Map<Material, Double> priceCache = new HashMap<>();
    private boolean cacheBuilt = false;

    @Override
    public String getPluginName() {
        return "ExcellentShop";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin("ExcellentShop");
            if (shopPlugin == null || !shopPlugin.isEnabled()) return false;

            Class.forName("su.nightexpress.excellentshop.ShopAPI");
            Class.forName("su.nightexpress.excellentshop.virtualshop.VirtualShopModule");
            return ShopAPI.isInitialized();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            plugin.debug("ExcellentShop API not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing ExcellentShop integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        if (material == null || material.isAir()) return 0.0;

        try {
            if (!cacheBuilt) buildCache();
            return priceCache.getOrDefault(material, 0.0);
        } catch (Exception e) {
            plugin.debug("Error getting sell price for " + material + " from ExcellentShop: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Scans all virtual shop products and caches the best (highest) sell price
     * per Material. Only considers sellable, item-type products.
     * Called lazily on first price lookup so the shop data is fully loaded.
     */
    private void buildCache() {
        priceCache.clear();
        cacheBuilt = true;

        try {
            VirtualShopModule virtualShop = ShopAPI.getVirtualShop();
            if (virtualShop == null) return;

            virtualShop.getShops().forEach(shop ->
                shop.getProductMap().values().forEach(product -> {
                    if (!product.isSellable()) return;
                    if (product.getContent().type() != ContentType.ITEM) return;

                    Material material = product.getPreview().getType();
                    if (material.isAir()) return;

                    double sellPrice = product.getSellPrice();
                    if (sellPrice <= 0) return;

                    // Keep the highest sell price if the same material appears in multiple shops
                    priceCache.merge(material, sellPrice, Math::max);
                })
            );

            plugin.getLogger().info("[ExcellentShop] Price cache built: " + priceCache.size() + " materials indexed.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to build ExcellentShop price cache: " + e.getMessage());
        }
    }

    /**
     * Invalidates the price cache. Call this when shop products may have changed
     * (e.g. after a plugin reload).
     */
    public void invalidateCache() {
        priceCache.clear();
        cacheBuilt = false;
    }
}
