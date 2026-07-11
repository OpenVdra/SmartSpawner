package github.nighter.smartspawner.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.nighter.smartspawner.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpdateChecker implements Listener {
    private final JavaPlugin plugin;
    private final String projectId = "9tQwxSFr";
    // SmartSpawner 2.0.0+ is a separate product (SmartSpawner2); 1.x users are never notified about it.
    private static final int MAX_NOTIFIABLE_MAJOR_VERSION = 1;
    private boolean updateAvailable = false;
    private final String currentVersion;
    private String latestVersion = "";
    private String downloadUrl = "";

    // ANSI codes for console output
    private static final String RESET  = "[0m";
    private static final String BOLD   = "[1m";
    private static final String DIM    = "[2m";
    private static final String GREEN  = "[92m";
    private static final String YELLOW = "[33m";
    private static final String CYAN   = "[96m";

    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate) {
                displayConsoleUpdateMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    private void displayConsoleUpdateMessage() {
        String line = DIM + "-----------------------------------------------------" + RESET;
        plugin.getLogger().info(line);
        plugin.getLogger().info(BOLD + "  SmartSpawner - Update Available" + RESET);
        plugin.getLogger().info(line);
        plugin.getLogger().info("  Current version  :  " + YELLOW + currentVersion + RESET);
        plugin.getLogger().info("  Latest version   :  " + GREEN  + latestVersion  + RESET);
        plugin.getLogger().info("  Download         :  " + CYAN
                + "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion + RESET);
        plugin.getLogger().info(line);
    }

    /**
     * SmartSpawner 2.0.0+ is a different product (SmartSpawner2), so releases with a major
     * version above MAX_NOTIFIABLE_MAJOR_VERSION must never surface as an "update" here.
     */
    private boolean isNotifiableRelease(String versionNumber) {
        return new Version(versionNumber).getMajor() <= MAX_NOTIFIABLE_MAJOR_VERSION;
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI
                        .create("https://api.modrinth.com/v2/project/" + projectId + "/version")
                        .toURL()
                        .openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "SmartSpawner-UpdateChecker/1.0");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Update check failed with HTTP " + connection.getResponseCode());
                    return false;
                }

                String response;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    response = reader.lines().collect(Collectors.joining("\n"));
                }

                JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
                if (versions.isEmpty()) {
                    return false;
                }

                JsonObject latestVersionObj = null;
                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();
                    if (!"release".equals(version.get("version_type").getAsString())) {
                        continue;
                    }
                    if (!isNotifiableRelease(version.get("version_number").getAsString())) {
                        continue;
                    }
                    if (latestVersionObj == null) {
                        latestVersionObj = version;
                    } else {
                        String currentDate = latestVersionObj.get("date_published").getAsString();
                        String newDate     = version.get("date_published").getAsString();
                        if (newDate.compareTo(currentDate) > 0) {
                            latestVersionObj = version;
                        }
                    }
                }

                if (latestVersionObj == null) {
                    return false;
                }

                latestVersion = latestVersionObj.get("version_number").getAsString();
                downloadUrl   = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;

                updateAvailable = new Version(latestVersion).compareTo(new Version(currentVersion)) > 0;
                return updateAvailable;

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
                return false;
            }
        });
    }

    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !player.hasPermission("smartspawner.admin")) {
            return;
        }

        TextColor purple = TextColor.fromHexString("#9b72cf");
        TextColor green  = TextColor.fromHexString("#57d98e");
        TextColor yellow = TextColor.fromHexString("#f0c857");
        TextColor white  = TextColor.fromHexString("#e6e6e6");
        TextColor gray   = TextColor.fromHexString("#555577");

        Component line = Component.text("----------------------------------------").color(gray);

        Component header = Component.text("SmartSpawner").color(purple)
                .append(Component.text(" - Update Available").color(white));

        Component versions = Component.text("  Current: ").color(white)
                .append(Component.text(currentVersion).color(yellow))
                .append(Component.text("  ->  Latest: ").color(white))
                .append(Component.text(latestVersion).color(green));

        Component download = Component.text("  [ Click here to download v" + latestVersion + " ]")
                .color(purple)
                .clickEvent(ClickEvent.openUrl(downloadUrl))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Open download page on Modrinth").color(white)));

        player.sendMessage(Component.empty());
        player.sendMessage(line);
        player.sendMessage(header);
        player.sendMessage(versions);
        player.sendMessage(download);
        player.sendMessage(line);
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();

        notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

        if (notifiedPlayers.containsKey(playerId)) {
            return;
        }

        if (updateAvailable) {
            Scheduler.runTaskLater(() -> {
                sendUpdateNotification(player);
                notifiedPlayers.put(playerId, today);
            }, 40L);
        } else {
            checkForUpdates().thenAccept(hasUpdate -> {
                if (hasUpdate) {
                    Scheduler.runTask(() -> {
                        sendUpdateNotification(player);
                        notifiedPlayers.put(playerId, today);
                    });
                }
            });
        }
    }
}
