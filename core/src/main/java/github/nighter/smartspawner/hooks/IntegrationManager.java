package github.nighter.smartspawner.hooks;

import com.plotsquared.core.PlotAPI;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.drops.MythicMobsHook;
import github.nighter.smartspawner.hooks.protections.api.IridiumSkyblock;
import github.nighter.smartspawner.hooks.protections.api.Lands;
import github.nighter.smartspawner.hooks.protections.api.PlotSquared;
import github.nighter.smartspawner.hooks.protections.api.SuperiorSkyblock2;
import github.nighter.smartspawner.hooks.rpg.AuraSkillsIntegration;
import github.nighter.smartspawner.hooks.bedrock.FloodgateHook;
import lombok.Getter;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import fr.xyness.SCS.API.SimpleClaimSystemAPI_Provider;
import fr.xyness.SCS.SimpleClaimSystem;
import fr.xyness.SimpleClaimSystem.API.SCS_API_Provider;
import fr.xyness.SimpleClaimSystem.API.SCS_API;

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
    private boolean hasMinePlots = false;
    private boolean hasMythicMobs = false;
    private boolean hasIridiumSkyblock = false;
    private boolean hasPlotSquared = false;
    private boolean hasResidence = false;

    // Integration plugin flags
    private boolean hasAuraSkills = false;
    private boolean hasFloodgate = false;

    // Integration instances
    public AuraSkillsIntegration auraSkillsIntegration;
    public FloodgateHook floodgateHook;

    public IntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initializeIntegrations() {
        checkProtectionPlugins();
        checkIntegrationPlugins();
    }

    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            return worldGuardPlugin != null && worldGuardPlugin.isEnabled();
        }, true);

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            Plugin griefPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            return griefPlugin instanceof GriefPrevention;
        }, true);

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new Lands(plugin);
                return true;
            }
            return false;
        }, true);

        hasTowny = checkPlugin("Towny", () -> {
            Plugin townyPlugin = Bukkit.getPluginManager().getPlugin("Towny");
            return townyPlugin != null && townyPlugin.isEnabled();
        }, true);

        hasSuperiorSkyblock2 = checkPlugin("SuperiorSkyblock2", () -> {
            Plugin superiorSkyblock2 = Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2");
            if(superiorSkyblock2 != null) {
                SuperiorSkyblock2 ssb2 = new SuperiorSkyblock2();
                Bukkit.getPluginManager().registerEvents(ssb2, plugin);
                return true;
            }
            return false;
        }, true);

        hasBentoBox = checkPlugin("BentoBox", () -> {
            Plugin bentoPlugin = Bukkit.getPluginManager().getPlugin("BentoBox");
            return bentoPlugin != null && bentoPlugin.isEnabled();
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
            return SimpleClaimSystemAPI_Provider.getAPI() != null;
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
                return api != null;
            }
            return false;
        }, true);

        hasMinePlots = checkPlugin("minePlots", () -> {
            Plugin mP = Bukkit.getPluginManager().getPlugin("minePlots");
            return mP != null && mP.isEnabled();
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
                return true;
            }
            return false;
        }, true);

        hasResidence = checkPlugin("Residence", () -> {
            Plugin residence = Bukkit.getPluginManager().getPlugin("Residence");
            return residence != null && residence.isEnabled();
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

        hasFloodgate = checkPlugin("Floodgate", () -> {
            Plugin floodgatePlugin = Bukkit.getPluginManager().getPlugin("floodgate");
            if (floodgatePlugin != null && floodgatePlugin.isEnabled()) {
                this.floodgateHook = new FloodgateHook(plugin);
                return this.floodgateHook.isEnabled();
            } else {
                this.floodgateHook = null;
                return false;
            }
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
