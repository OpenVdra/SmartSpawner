package github.nighter.smartspawner.commands.near;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages per-player spawner highlight sessions.
 * Scans asynchronously and renders BlockDisplay entities visible only to
 * the requesting player, with a glow outline visible through walls.
 */
public class SpawnerHighlightManager implements Listener {

    public static final int MAX_RADIUS = 10000;
    private static final int MAX_HIGHLIGHTS = 10000;
    private static final long HIGHLIGHT_DURATION_TICKS = 30 * 20L; // 30 seconds
    private static final long BOSSBAR_RESULT_TICKS = 5 * 20L; // 5 seconds
    
    private static final String BOSSBAR_ANALYZING_FALLBACK = "Scanning nearby spawners... {percent}%";
    private static final String BOSSBAR_FOUND_FALLBACK = "Found {count} spawner(s) within {radius} blocks.";
    private static final String BOSSBAR_NOT_FOUND_FALLBACK = "No spawners found within {radius} blocks.";
    private static final String VIEW_GUI_BUTTON_FALLBACK = " [View in GUI]";

    private final SmartSpawner plugin;
    private final Map<UUID, ScanSession> activeSessions = new ConcurrentHashMap<>();

    public SpawnerHighlightManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    private static final class ScanSession {
        final UUID playerUUID;
        final BossBar bossBar;
        final CopyOnWriteArrayList<BlockDisplay> highlights = new CopyOnWriteArrayList<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile Scheduler.Task expiryTask;
        volatile List<SpawnerData> scannedSpawners = Collections.emptyList();

        ScanSession(UUID playerUUID, BossBar bossBar) {
            this.playerUUID = playerUUID;
            this.bossBar = bossBar;
        }
    }

    /**
     * Start a new scan for {@code player} in radius {@code radius}.
     * Any previous session for this player is silently cancelled first.
     * Must be called from the main / region thread.
     */
    public void startScan(Player player, int radius) {
        UUID uuid = player.getUniqueId();

        ScanSession existing = activeSessions.remove(uuid);
        if (existing != null) {
            existing.cancelled.set(true);
            cleanupSession(existing, player);
        }

        final Location playerLoc = player.getLocation().clone();
        final String worldName = playerLoc.getWorld().getName();
        final double radiusSq = (double) radius * radius;
        final int finalRadius = radius;

        BossBar bossBar = BossBar.bossBar(
                Component.text(plugin.getLanguageManager().getCommandConfig(
                        "near.bossbar.analyzing", BOSSBAR_ANALYZING_FALLBACK,
                        Map.of("percent", "0")), NamedTextColor.AQUA),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );

        ScanSession session = new ScanSession(uuid, bossBar);
        activeSessions.put(uuid, session);
        player.showBossBar(bossBar);
        player.updateCommands();

        plugin.getMessageService().sendMessage(player, "near.scan_start",
                Map.of("radius", String.valueOf(radius)));

        Scheduler.runTaskAsync(() -> {
            if (session.cancelled.get()) return;

            Set<SpawnerData> worldSpawners = plugin.getSpawnerManager().getSpawnersInWorld(worldName);

            if (worldSpawners == null || worldSpawners.isEmpty()) {
                Scheduler.runTask(() -> {
                    if (!session.cancelled.get())
                        finalizeScan(player, session, Collections.emptyList(), finalRadius);
                });
                return;
            }

            List<SpawnerData> snapshot = new ArrayList<>(worldSpawners);
            int total = snapshot.size();
            List<SpawnerData> nearby = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                if (session.cancelled.get()) return;

                SpawnerData spawner = snapshot.get(i);
                Location loc = spawner.getSpawnerLocation();
                if (loc == null || loc.getWorld() == null) continue;

                double dx = loc.getX() - playerLoc.getX();
                double dy = loc.getY() - playerLoc.getY();
                double dz = loc.getZ() - playerLoc.getZ();
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    nearby.add(spawner);
                    if (nearby.size() >= MAX_HIGHLIGHTS) break;
                }

                if (i % 50 == 0 || i == total - 1) {
                    float progress = (float) (i + 1) / total;
                    int pct = (int) (progress * 100);
                    bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                            "near.bossbar.analyzing", BOSSBAR_ANALYZING_FALLBACK,
                            Map.of("percent", String.valueOf(pct))), NamedTextColor.AQUA));
                    bossBar.progress(progress);
                }
            }

            if (session.cancelled.get()) return;

            final List<SpawnerData> result = nearby;
            Scheduler.runTask(() -> {
                if (!session.cancelled.get())
                    finalizeScan(player, session, result, finalRadius);
            });
        });
    }

    /**
     * Cancel the active scan for {@code player} and remove all highlights.
     * Must be called from the main / region thread.
     */
    public void cancelScan(Player player) {
        ScanSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.getMessageService().sendMessage(player, "near.no_active_scan");
            return;
        }
        session.cancelled.set(true);
        cleanupSession(session, player);
        player.updateCommands();
        plugin.getMessageService().sendMessage(player, "near.scan_cancelled");
    }

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Returns an unmodifiable snapshot of the spawners from the player's active session,
     * or an empty list if no session exists.
     */
    public List<SpawnerData> getSessionSpawners(UUID uuid) {
        ScanSession session = activeSessions.get(uuid);
        return session != null ? Collections.unmodifiableList(session.scannedSpawners) : Collections.emptyList();
    }

    private void finalizeScan(Player player, ScanSession session,
                               List<SpawnerData> spawners, int radius) {
        if (!player.isOnline()) {
            cleanupSession(session, null);
            return;
        }

        int count = spawners.size();
        session.scannedSpawners = new ArrayList<>(spawners);

        if (count == 0) {
            session.bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                    "near.bossbar.not_found", BOSSBAR_NOT_FOUND_FALLBACK,
                    Map.of("radius", String.valueOf(radius))), NamedTextColor.RED));
            session.bossBar.color(BossBar.Color.RED);
        } else {
            session.bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                    "near.bossbar.found", BOSSBAR_FOUND_FALLBACK,
                    Map.of("count", String.valueOf(count), "radius", String.valueOf(radius))), NamedTextColor.GREEN));
            session.bossBar.color(BossBar.Color.GREEN);
        }
        session.bossBar.progress(1f);

        for (SpawnerData spawner : spawners) {
            if (session.cancelled.get()) return;
            Location loc = spawner.getSpawnerLocation();
            if (loc != null) spawnHighlight(player, session, loc);
        }

        Map<String, String> resultPlaceholders = Map.of("count", String.valueOf(count), "radius", String.valueOf(radius));
        if (count > 0) {
            String foundMsg = plugin.getLanguageManager().getMessage("near.scan_found", resultPlaceholders);
            Component textPart = (foundMsg != null && !foundMsg.startsWith("Missing"))
                    ? LegacyComponentSerializer.legacySection().deserialize(foundMsg)
                    : Component.empty();

            Component viewGuiHint = Component.text()
                    .append(Component.text(
                            plugin.getLanguageManager().getCommandConfig("near.view_gui.button", VIEW_GUI_BUTTON_FALLBACK),
                            NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.callback(audience -> {
                                if (audience instanceof Player clickPlayer) {
                                    plugin.getNearResultGUI().openNearResultGUI(clickPlayer, 1);
                                }
                            }))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text(plugin.getLanguageManager().getCommandConfig(
                                            "near.view_gui.hover",
                                            "Click to browse the {count} spawner(s) found nearby",
                                            Map.of("count", String.valueOf(count))),
                                            NamedTextColor.GRAY))))
                    .build();

            player.sendMessage(Component.text().append(textPart).append(viewGuiHint).build());

            String soundKey = plugin.getLanguageManager().getSound("near.scan_found");
            if (soundKey != null) {
                try {
                    player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        } else {
            plugin.getMessageService().sendMessage(player, "near.scan_none", resultPlaceholders);
        }

        Scheduler.runTaskLater(() -> {
            Player p = plugin.getServer().getPlayer(session.playerUUID);
            if (p != null && p.isOnline()) {
                Scheduler.runEntityTask(p, () -> p.hideBossBar(session.bossBar));
            }
        }, BOSSBAR_RESULT_TICKS);

        session.expiryTask = Scheduler.runTaskLater(() -> {
            ScanSession current = activeSessions.get(session.playerUUID);
            if (current != session) return;
            activeSessions.remove(session.playerUUID);
            session.cancelled.set(true);
            Player p = plugin.getServer().getPlayer(session.playerUUID);
            cleanupSession(session, p);
            if (p != null && p.isOnline()) {
                p.updateCommands();
                plugin.getMessageService().sendMessage(p, "near.highlights_expired");
            }
        }, HIGHLIGHT_DURATION_TICKS);
    }

    /**
     * Schedules BlockDisplay spawning on the chunk's region thread (Folia-safe),
     * then shows it to the player on their entity region thread.
     */
    private void spawnHighlight(Player player, ScanSession session, Location loc) {
        Scheduler.runLocationTask(loc, () -> {
            if (session.cancelled.get() || !player.isOnline()) return;
            World world = loc.getWorld();
            if (world == null) return;

            Location spawnLoc = loc.getBlock().getLocation();

            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(Material.SPAWNER.createBlockData());
                bd.setGlowing(true);
                bd.setVisibleByDefault(false);
                bd.setPersistent(false);
            });

            session.highlights.add(display);

            Scheduler.runEntityTask(player, () -> {
                if (player.isOnline()) player.showEntity(plugin, display);
            });
        });
    }

    /**
     * Removes bossbar and all highlight entities for {@code session}.
     * {@code player} may be {@code null} if they already disconnected.
     * Entity removal is dispatched to each entity's region thread (Folia-safe).
     */
    private void cleanupSession(ScanSession session, Player player) {
        if (player != null && player.isOnline()) {
            Scheduler.runEntityTask(player, () -> player.hideBossBar(session.bossBar));
        }
        if (session.expiryTask != null) {
            session.expiryTask.cancel();
            session.expiryTask = null;
        }
        List<BlockDisplay> copy = new ArrayList<>(session.highlights);
        session.highlights.clear();
        for (BlockDisplay bd : copy) {
            if (bd.isValid()) {
                Scheduler.runEntityTask(bd, () -> {
                    if (bd.isValid()) bd.remove();
                });
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ScanSession session = activeSessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;
        session.cancelled.set(true);
        cleanupSession(session, null);
    }

    /** Called when the plugin is disabled to tear down all active sessions. */
    public void cleanup() {
        for (ScanSession session : activeSessions.values()) {
            session.cancelled.set(true);
            if (session.expiryTask != null) session.expiryTask.cancel();
            for (BlockDisplay bd : session.highlights) {
                if (bd.isValid()) bd.remove();
            }
            session.highlights.clear();
        }
        activeSessions.clear();
    }
}
