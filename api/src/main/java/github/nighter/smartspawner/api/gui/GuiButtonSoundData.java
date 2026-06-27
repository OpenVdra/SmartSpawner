package github.nighter.smartspawner.api.gui;

import lombok.Getter;

import java.util.Objects;

/**
 * Immutable sound configuration for a GUI button click.
 */
@Getter
public class GuiButtonSoundData {
    private final String name;
    private final float volume;
    private final float pitch;

    /**
     * Creates an immutable GUI button sound configuration.
     *
     * @param name Bukkit sound key
     * @param volume sound volume
     * @param pitch sound pitch
     */
    public GuiButtonSoundData(String name, float volume, float pitch) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sound name cannot be blank");
        }
        if (!Float.isFinite(volume) || volume < 0.0f) {
            throw new IllegalArgumentException("Sound volume must be finite and non-negative");
        }
        if (!Float.isFinite(pitch) || pitch < 0.0f) {
            throw new IllegalArgumentException("Sound pitch must be finite and non-negative");
        }
        this.name = name;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GuiButtonSoundData other)) {
            return false;
        }
        return Float.compare(volume, other.volume) == 0
                && Float.compare(pitch, other.pitch) == 0
                && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, volume, pitch);
    }
}
