package github.nighter.smartspawner.hooks;

import com.plotsquared.core.PlotAPI;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.drops.MythicMobsHook;
import github.nighter.smartspawner.hooks.items.CustomItemRegistry;
import github.nighter.smartspawner.hooks.items.providers.mmoitems.MMOItemsProvider;
import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import github.nighter.smartspawner.hooks.protections.api.*;
import github.nighter.smartspawner.hooks.rpg.AuraSkillsIntegration;
import lombok.Getter;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import fr.xyness.SCS.API.SimpleClaimSystemAPI_Provider;
import fr.xyness.SCS.SimpleClaimSystem;
import fr.xyness.SimpleClaimSystem.API.SCS_API_Provider;
import fr.xyness.SimpleClaimSystem.API.SCS_API;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Getter
public class IntegrationManager {
    private final SmartSpawner plugin;

    // Protection plugin flags
    private boolean hasTowny = false;
    private boolean hasLands = false;
    private boolean hasWorldGuard = false;
    private boolean hasGriefPrevention = false;
    private boolean hasSuperiorSkyblock2 = false;
    private boolean hasBentoBox = false;
    private boolean hasSimpleClaimSystem = false;
    private boolean hasSimpleClaimSystem2 = false;
    private boolean hasRedProtect = false;
    private boolean hasMinePlots = false;
    private boolean hasMythicMobs = false;
    private boolean hasIridiumSkyblock = false;
    private boolean hasPlotSquared = false;
    private boolean hasResidence = false;
    private boolean hasFactions = false;
    private boolean hasBlockLocker = false;

    // Active protection hooks, one per detected plugin, iterated by the Check* entry points.
    private final List<ProtectionHook> protectionHooks = new ArrayList<>();

    // Integration plugin flags
    private boolean hasAuraSkills = false;
    private boolean hasMMOItems = false;

    // Integration instances
    public AuraSkillsIntegration auraSkillsIntegration;

    public IntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initializeIntegrations() {
        CustomItemRegistry.clear();
        checkProtectionPlugins();
        checkIntegrationPlugins();
    }

    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (worldGuardPlugin != null && worldGuardPlugin.isEnabled()) {
                protectionHooks.add(new WorldGuard());
                return true;
            }
            return false;
        }, true);

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            Plugin griefPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (griefPlugin instanceof GriefPrevention) {
                protectionHooks.add(new github.nighter.smartspawner.hooks.protections.api.GriefPrevention());
                return true;
            }
            return false;
        }, true);

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                protectionHooks.add(new Lands(plugin));
                return true;
            }
            return false;
        }, true);

        hasTowny = checkPlugin("Towny", () -> {
            Plugin townyPlugin = Bukkit.getPluginManager().getPlugin("Towny");
            if (townyPlugin != null && townyPlugin.isEnabled()) {
                protectionHooks.add(new Towny());
                return true;
            }
            return false;
        }, true);

        hasSuperiorSkyblock2 = checkPlugin("SuperiorSkyblock2", () -> {
            Plugin superiorSkyblock2 = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
            if(superiorSkyblock2 != null) {
                SuperiorSkyblock2 ssb2 = new SuperiorSkyblock2();
                Bukkit.getPluginManager().registerEvents(ssb2, plugin);
                protectionHooks.add(ssb2);
                return true;
            }
            return false;
        }, true);

        hasBentoBox = checkPlugin("BentoBox", () -> {
            Plugin bentoPlugin = Bukkit.getPluginManager().getPlugin("BentoBox");
            if (bentoPlugin != null && bentoPlugin.isEnabled()) {
                protectionHooks.add(new BentoBoxAPI());
                return true;
            }
            return false;
        }, true);

        hasSimpleClaimSystem = checkPlugin("SimpleClaimSystem", () -> {
            Plugin simpleClaimPlugin = Bukkit.getPluginManager().getPlugin("SimpleClaimSystem");
            if (simpleClaimPlugin == null || !simpleClaimPlugin.isEnabled()) {
                return false;
            }
            // Prevent SimpleClaimSystem paid version (2.x.x)
            if (simpleClaimPlugin.getPluginMeta().getVersion().startsWith("2.")) {
                return false;
            }
            SimpleClaimSystemAPI_Provider.initialize((SimpleClaimSystem) simpleClaimPlugin);
            if (SimpleClaimSystemAPI_Provider.getAPI() != null) {
                protectionHooks.add(new github.nighter.smartspawner.hooks.protections.api.SimpleClaimSystem());
                return true;
            }
            return false;
        }, true);

        hasSimpleClaimSystem2 = checkPlugin("SimpleClaimSystem", () -> {
            Plugin simpleClaimPlugin = Bukkit.getPluginManager().getPlugin("SimpleClaimSystem");
            if (simpleClaimPlugin == null || !simpleClaimPlugin.isEnabled()) {
                return false;
            }
            // Prevent SimpleClaimSystem free version (1.x.x)
            if (simpleClaimPlugin.getPluginMeta().getVersion().startsWith("1.")) {
                return false;
            }
            if (SCS_API_Provider.isRegistered()) {
                SCS_API api = SCS_API_Provider.get();
                if (api != null) {
                    protectionHooks.add(new SimpleClaimSystem2());
                    return true;
                }
            }
            return false;
        }, true);

        hasRedProtect = checkPlugin("RedProtect", () -> {
            Plugin pRP = Bukkit.getPluginManager().getPlugin("RedProtect");
            if (pRP != null && pRP.isEnabled()) {
                protectionHooks.add(new RedProtectAPI());
                return true;
            }
            return false;
        }, true);

        hasMinePlots = checkPlugin("minePlots", () -> {
            Plugin mP = Bukkit.getPluginManager().getPlugin("minePlots");
            if (mP != null && mP.isEnabled()) {
                protectionHooks.add(new MinePlots());
                return true;
            }
            return false;
        }, true);

        hasMythicMobs = checkPlugin("MythicMobs", () -> {
            Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if(mm != null && mm.isEnabled()) {
                Bukkit.getPluginManager().registerEvents(new MythicMobsHook(), SmartSpawner.getInstance());
                return true;
            }
            return false;
        }, true);

        hasIridiumSkyblock = checkPlugin("IridiumSkyblock", () -> {
            Plugin is = Bukkit.getPluginManager().getPlugin("IridiumSkyblock");
            if(is != null && is.isEnabled()) {
                IridiumSkyblock.init(plugin);
                protectionHooks.add(new IridiumSkyblock());
                return true;
            }
            return false;
        }, true);

        hasPlotSquared = checkPlugin("PlotSquared", () -> {
            Plugin is = Bukkit.getPluginManager().getPlugin("PlotSquared");
            if(is != null && is.isEnabled()) {
                PlotAPI api = new PlotAPI();
                PlotSquared ps = new PlotSquared();
                api.registerListener(ps);
                Bukkit.getPluginManager().registerEvents(ps, SmartSpawner.getInstance());
                protectionHooks.add(ps);
                return true;
            }
            return false;
        }, true);

        hasResidence = checkPlugin("Residence", () -> {
            Plugin residence = Bukkit.getPluginManager().getPlugin("Residence");
            if (residence != null && residence.isEnabled()) {
                protectionHooks.add(new Residence());
                return true;
            }
            return false;
        }, true);

        hasFactions = checkPlugin("FactionsUUID", () -> {
            Plugin factions = Bukkit.getPluginManager().getPlugin("FactionsUUID");
            if (factions != null && factions.isEnabled()) {
                protectionHooks.add(new Factions());
                return true;
            }
            return false;
        }, true);

        hasBlockLocker = checkPlugin("BlockLocker", () -> {
            Plugin blockLocker = Bukkit.getPluginManager().getPlugin("BlockLocker");
            if (blockLocker != null && blockLocker.isEnabled()) {
                protectionHooks.add(new BlockLocker());
                return true;
            }
            return false;
        }, true);

    }

    private void checkIntegrationPlugins() {
        hasAuraSkills = checkPlugin("AuraSkills", () -> {
            Plugin auraSkillsPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
            if (auraSkillsPlugin != null && auraSkillsPlugin.isEnabled()) {
                this.auraSkillsIntegration = new AuraSkillsIntegration(plugin);
                return true;
            } else {
                this.auraSkillsIntegration = null;
                return false;
            }
        }, true);

        hasMMOItems = checkPlugin("MMOItems", () -> {
            Plugin mmoItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsPlugin != null && mmoItemsPlugin.isEnabled()) {
                MMOItemsProvider provider = new MMOItemsProvider();
                if (provider.isAvailable()) {
                    CustomItemRegistry.register(provider);
                    return true;
                }
            }
            return false;
        }, true);
    }

    private boolean checkPlugin(String pluginName, PluginCheck checker, boolean logSuccess) {
        try {
            if (checker.check()) {
                if (logSuccess) {
                    plugin.getLogger().info(pluginName + " integration enabled successfully!");
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize " + pluginName + " integration", e);
        }
        return false;
    }

    public void reload() {
        if (auraSkillsIntegration != null) {
            auraSkillsIntegration.reloadConfig();
        }
    }

    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }
}
