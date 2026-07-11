package github.nighter.smartspawner.updates;

import org.jetbrains.annotations.NotNull;

public class Version implements Comparable<Version> {
    private final int[] parts;
    private static final int MAX_PARTS = 4; // Updated to support 4 parts

    public Version(String version) {
        // Remove any non-numeric prefix (e.g., "v1.0.0" -> "1.0.0")
        version = version.replaceAll("[^0-9.].*$", "")
                .replaceAll("^[^0-9]*", "");

        String[] split = version.split("\\.");
        parts = new int[MAX_PARTS]; // Initialize array with 4 parts

        // Parse each part, defaulting to 0 if not present or not a valid number
        for (int i = 0; i < MAX_PARTS; i++) {
            if (i < split.length) {
                try {
                    parts[i] = Integer.parseInt(split[i]);
                } catch (NumberFormatException e) {
                    parts[i] = 0;
                }
            } else {
                parts[i] = 0; // Fill remaining parts with 0
            }
        }
    }


    public int getMajor() {
        return parts[0];
    }

    @Override
    public int compareTo(@NotNull Version other) {
        // Compare all 4 parts
        for (int i = 0; i < MAX_PARTS; i++) {
            if (parts[i] != other.parts[i]) {
                return parts[i] - other.parts[i];
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Always include the first three parts
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }

        // Only include the fourth part if it's non-zero
        if (parts[3] > 0) {
            sb.append('.').append(parts[3]);
        }

        return sb.toString();
    }
}