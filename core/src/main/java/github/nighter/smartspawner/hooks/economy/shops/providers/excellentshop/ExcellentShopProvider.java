package github.nighter.smartspawner.hooks.economy.shops.providers.excellentshop;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import su.nightexpress.excellentshop.ShopAPI;
import su.nightexpress.excellentshop.api.product.ContentType;
import su.nightexpress.excellentshop.virtualshop.VirtualShopModule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class ExcellentShopProvider implements ShopProvider, Listener {

    private final SmartSpawner plugin;

    // ConcurrentHashMap for Folia thread safety
    private final Map<Material, Double> priceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean cacheBuilt = new AtomicBoolean(false);

    @Override
    public String getPluginName() {
        return "ExcellentShop";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin("ExcellentShop");
            if (shopPlugin == null) {
                plugin.getLogger().info("[ExcellentShop] Plugin not found on server.");
                return false;
            }
            if (!shopPlugin.isEnabled()) {
                plugin.getLogger().info("[ExcellentShop] Plugin found but not enabled.");
                return false;
            }

            Class.forName("su.nightexpress.excellentshop.ShopAPI");
            Class.forName("su.nightexpress.excellentshop.virtualshop.VirtualShopModule");

            // Register ServerLoadEvent listener to build cache after full startup
            Bukkit.getPluginManager().registerEvents(this, plugin);

            plugin.getLogger().info("[ExcellentShop] Integration ready. Price cache will build after server finishes loading.");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            plugin.getLogger().warning("[ExcellentShop] API classes not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("[ExcellentShop] Error initializing: " + e.getMessage());
        }
        return false;
    }

    /**
     * Build the cache after the server has fully started so ExcellentShop's
     * async data load is complete and all prices are populated.
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        buildCache();
    }

    @Override
    public double getSellPrice(Material material) {
        if (material == null || material.isAir()) return 0.0;

        try {
            // If cache hasn't been built yet (e.g. first call before ServerLoadEvent), build it now
            if (!cacheBuilt.get()) buildCache();
            return priceCache.getOrDefault(material, 0.0);
        } catch (Exception e) {
            plugin.debug("Error getting sell price for " + material + " from ExcellentShop: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Scans all virtual shop products and caches the best (highest) sell price
     * per Material. Only considers sellable, item-type products.
     */
    private void buildCache() {
        if (!cacheBuilt.compareAndSet(false, true)) return; // Only build once

        priceCache.clear();

        try {
            VirtualShopModule virtualShop = ShopAPI.getVirtualShop();
            if (virtualShop == null) {
                plugin.getLogger().warning("[ExcellentShop] VirtualShop module not available.");
                cacheBuilt.set(false); // Allow retry
                return;
            }

            virtualShop.getShops().forEach(shop ->
                shop.getProductMap().values().forEach(product -> {
                    if (!product.isSellable()) return;
                    if (product.getContent().type() != ContentType.ITEM) return;

                    Material material = product.getPreview().getType();
                    if (material.isAir()) return;

                    double sellPrice = product.getSellPrice();
                    if (sellPrice < 0) return; // -1 = disabled; 0 is valid

                    // Keep the highest sell price if the same material is in multiple shops
                    priceCache.merge(material, sellPrice, Math::max);
                })
            );

            plugin.getLogger().info("[ExcellentShop] Price cache built: " + priceCache.size() + " materials indexed.");
            if (priceCache.isEmpty()) {
                plugin.getLogger().warning("[ExcellentShop] Cache is empty — check that your virtual shops have sellable products.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ExcellentShop] Failed to build price cache: " + e.getMessage());
            cacheBuilt.set(false); // Allow retry on next call
        }
    }

    /**
     * Invalidates the price cache — call after shop prices change.
     */
    public void invalidateCache() {
        priceCache.clear();
        cacheBuilt.set(false);
    }
}
