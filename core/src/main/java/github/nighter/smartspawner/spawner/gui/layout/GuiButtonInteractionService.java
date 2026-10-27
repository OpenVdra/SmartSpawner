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
    private static final long ANTI_SPAM_NANOS = 100_000_000L;

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
        long now = System.nanoTime();
        if (isAntiSpamCoolingDown(player, now)) {
            return false;
        }

        long cooldownTicks = button.getCooldownTicks();
        if (cooldownTicks > 0L) {
            long cooldownNanos = cooldownTicks > Long.MAX_VALUE / NANOS_PER_TICK
                    ? Long.MAX_VALUE
                    : cooldownTicks * NANOS_PER_TICK;

            Long previous = getLastButtonClick(player, layoutType, button);
            if (previous != null && now - previous < cooldownNanos) {
                long remainingNanos = cooldownNanos - (now - previous);
                plugin.getMessageService().sendMessage(player, "general.action_not_ready",
                        Map.of("time", formatRemainingTime(remainingNanos)));
                return false;
            }

            Map<GuiLayoutType, Map<String, Long>> playerClicks = lastClicks.computeIfAbsent(
                    player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
            Map<String, Long> layoutClicks = playerClicks.computeIfAbsent(
                    layoutType, ignored -> new ConcurrentHashMap<>());
            layoutClicks.put(button.getButtonType(), now);
        }

        lastInteractions.put(player.getUniqueId(), now);
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
     * Applies the global 100 ms GUI click debounce without checking button cooldown.
     */
    public boolean tryUseAntiSpam(Player player) {
        long now = System.nanoTime();
        if (isAntiSpamCoolingDown(player, now)) {
            return false;
        }

        lastInteractions.put(player.getUniqueId(), now);
        return true;
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

    private boolean isAntiSpamCoolingDown(Player player, long now) {
        Long previous = lastInteractions.get(player.getUniqueId());
        return previous != null && now - previous < ANTI_SPAM_NANOS;
    }

    private Long getLastButtonClick(Player player, GuiLayoutType layoutType, GuiButton button) {
        Map<GuiLayoutType, Map<String, Long>> playerClicks = lastClicks.get(player.getUniqueId());
        if (playerClicks == null) {
            return null;
        }

        Map<String, Long> layoutClicks = playerClicks.get(layoutType);
        return layoutClicks != null ? layoutClicks.get(button.getButtonType()) : null;
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
