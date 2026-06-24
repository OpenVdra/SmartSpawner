package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.gui.GuiButtonSoundData;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies layout-defined cooldown and sound behavior to GUI buttons.
 */
public class GuiButtonInteractionService implements Listener {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long ANTI_SPAM_NANOS = 300_000_000L;

    private final SmartSpawner plugin;
    private final Map<UUID, Long> lastInteractions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<GuiLayoutType, Map<String, Long>>> lastClicks =
            new ConcurrentHashMap<>();
    private final Set<String> invalidSoundWarnings = ConcurrentHashMap.newKeySet();

    public GuiButtonInteractionService(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to accept a button click.
     *
     * @return true when the action may continue, false while the button is cooling down
     */
    public boolean tryUse(Player player, GuiLayoutType layoutType, GuiButton button) {
        if (!tryUseAntiSpam(player)) {
            return false;
        }

        long cooldownTicks = button.getCooldownTicks();
        if (cooldownTicks > 0L) {
            long now = System.nanoTime();
            long cooldownNanos = cooldownTicks > Long.MAX_VALUE / NANOS_PER_TICK
                    ? Long.MAX_VALUE
                    : cooldownTicks * NANOS_PER_TICK;

            Map<GuiLayoutType, Map<String, Long>> playerClicks = lastClicks.computeIfAbsent(
                    player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
            Map<String, Long> layoutClicks = playerClicks.computeIfAbsent(
                    layoutType, ignored -> new ConcurrentHashMap<>());
            String buttonKey = button.getButtonType();
            while (true) {
                Long previous = layoutClicks.get(buttonKey);
                if (previous != null && now - previous < cooldownNanos) {
                    long remainingNanos = cooldownNanos - (now - previous);
                    plugin.getMessageService().sendMessage(player, "action_not_ready",
                            Map.of("time", formatRemainingTime(remainingNanos)));
                    return false;
                }

                if (previous == null) {
                    if (layoutClicks.putIfAbsent(buttonKey, now) == null) {
                        break;
                    }
                } else if (layoutClicks.replace(buttonKey, previous, now)) {
                    break;
                }
            }
        }

        return true;
    }

    public void playNavigateSound(Player player, GuiButton button) {
        playNavigateSound(player, button, "click");
    }

    public void playNavigateSound(Player player, GuiButton button, String clickType) {
        playSounds(player, button.getClickSounds(clickType));
    }

    public void playSuccessSound(Player player, GuiButton button) {
        playSuccessSound(player, button, "click");
    }

    public void playSuccessSound(Player player, GuiButton button, String clickType) {
        playSounds(player, button.getSuccessSounds(clickType));
    }

    public void playFailSound(Player player, GuiButton button) {
        playFailSound(player, button, "click");
    }

    public void playFailSound(Player player, GuiButton button, String clickType) {
        playSounds(player, button.getFailSounds(clickType));
    }

    /**
     * Applies the global 300 ms GUI click debounce without checking button cooldown.
     */
    public boolean tryUseAntiSpam(Player player) {
        long now = System.nanoTime();
        Long previous = lastInteractions.put(player.getUniqueId(), now);
        return previous == null || now - previous >= ANTI_SPAM_NANOS;
    }

    public void clear() {
        lastInteractions.clear();
        lastClicks.clear();
        invalidSoundWarnings.clear();
    }

    /**
     * Plays the configured open_sound from main_gui.yml, if set.
     */
    public void playOpenSound(Player player) {
        String openSound = plugin.getGuiLayoutConfig().getOpenSound();
        if (openSound != null) {
            playSound(player, new GuiButtonSoundData(openSound, 1.0f, 1.0f));
        }
    }

    private String formatRemainingTime(long remainingNanos) {
        long tenths = Math.max(1L, (remainingNanos + 99_999_999L) / 100_000_000L);
        if (tenths % 10L == 0L) {
            return (tenths / 10L) + "s";
        }
        return (tenths / 10L) + "." + (tenths % 10L) + "s";
    }

    private void playSounds(Player player, List<GuiButtonSoundData> sounds) {
        for (GuiButtonSoundData sound : sounds) {
            playSound(player, sound);
        }
    }

    private void playSound(Player player, GuiButtonSoundData sound) {
        try {
            player.playSound(player.getLocation(), sound.getName(), sound.getVolume(), sound.getPitch());
        } catch (Exception e) {
            if (invalidSoundWarnings.add(sound.getName())) {
                plugin.getLogger().warning(
                        "Invalid GUI button sound '" + sound.getName() + "': " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastInteractions.remove(playerId);
        lastClicks.remove(playerId);
    }
}
