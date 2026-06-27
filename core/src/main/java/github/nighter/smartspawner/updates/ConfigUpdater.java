package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ConfigUpdater {
    private static final String VERSION_KEY = "config_version";
    private final SmartSpawner plugin;

    public ConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        ConfigVersionService.updateFile(plugin, configFile, "config.yml", VERSION_KEY,
                this::migrateRenamedPaths);

        // Reload Bukkit's cached config after any write
        plugin.reloadConfig();
    }

    // ── Path migrations ──────────────────────────────────────────────────────

    private void migrateRenamedPaths(Map<String, Object> userValues) {
        List<Map.Entry<String, String>> renames = List.of(
                Map.entry("database.standalone.host",     "database.sql.host"),
                Map.entry("database.standalone.port",     "database.sql.port"),
                Map.entry("database.standalone.username", "database.sql.username"),
                Map.entry("database.standalone.password", "database.sql.password"),
                Map.entry("database.standalone.pool.maximum-size",           "database.sql.pool.maximum-size"),
                Map.entry("database.standalone.pool.minimum-idle",           "database.sql.pool.minimum-idle"),
                Map.entry("database.standalone.pool.connection-timeout",     "database.sql.pool.connection-timeout"),
                Map.entry("database.standalone.pool.max-lifetime",           "database.sql.pool.max-lifetime"),
                Map.entry("database.standalone.pool.idle-timeout",           "database.sql.pool.idle-timeout"),
                Map.entry("database.standalone.pool.keepalive-time",         "database.sql.pool.keepalive-time"),
                Map.entry("database.standalone.pool.leak-detection-threshold", "database.sql.pool.leak-detection-threshold"),
                Map.entry("custom_economy.enabled", "sell_integration.enabled"),
                Map.entry("custom_economy.currency", "sell_integration.currency"),
                Map.entry("custom_economy.coinsengine_currency", "sell_integration.excellenteconomy_currency"),
                Map.entry("custom_economy.price_source_mode", "sell_integration.price_source_mode"),
                Map.entry("custom_economy.shop_integration.enabled", "sell_integration.shop_integration.enabled"),
                Map.entry("custom_economy.shop_integration.preferred_plugin", "sell_integration.shop_integration.preferred_plugin"),
                Map.entry("custom_economy.custom_prices.enabled", "sell_integration.custom_prices.enabled"),
                Map.entry("custom_economy.custom_prices.default_price", "sell_integration.custom_prices.default_price"),
                Map.entry("spawner_break.auto_sell_and_claim_exp_on_break", "spawner_break.sell_and_xp_break")
        );

        for (Map.Entry<String, String> rename : renames) {
            String old = rename.getKey();
            String next = rename.getValue();
            if (userValues.containsKey(old) && !userValues.containsKey(next)) {
                userValues.put(next, userValues.remove(old));
                plugin.debug("Migrated config path: " + old + " → " + next);
            }
        }

        if (userValues.containsKey("sell_integration.coinsengine_currency")
                && !userValues.containsKey("sell_integration.excellenteconomy_currency")) {
            userValues.put("sell_integration.excellenteconomy_currency", userValues.remove("sell_integration.coinsengine_currency"));
            plugin.debug("Migrated config path: sell_integration.coinsengine_currency → sell_integration.excellenteconomy_currency");
        }

        if ("COINSENGINE".equals(userValues.get("sell_integration.currency"))) {
            userValues.put("sell_integration.currency", "EXCELLENTECONOMY");
            plugin.getLogger().info("Migrated sell_integration.currency: COINSENGINE → EXCELLENTECONOMY");
        }

        // DATABASE → MYSQL mode rename
        if ("DATABASE".equals(userValues.get("database.mode"))) {
            userValues.put("database.mode", "MYSQL");
            plugin.getLogger().info("Migrated database.mode: DATABASE → MYSQL");
        }
    }
}
