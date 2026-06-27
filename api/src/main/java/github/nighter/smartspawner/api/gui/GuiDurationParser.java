package github.nighter.smartspawner.api.gui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses GUI duration values using the same tick-based format as SmartSpawner's config.yml.
 */
public final class GuiDurationParser {
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60L;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60L;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24L;
    private static final long TICKS_PER_WEEK = TICKS_PER_DAY * 7L;
    private static final long TICKS_PER_MONTH = TICKS_PER_DAY * 30L;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365L;

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)y)?_?(?:(\\d+)mo)?_?(?:(\\d+)w)?_?(?:(\\d+)d)?_?"
                    + "(?:(\\d+)h)?_?(?:(\\d+)m)?_?(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE);

    private GuiDurationParser() {
    }

    /**
     * Parses a duration to ticks. Plain numeric values are treated as ticks.
     *
     * @param value duration such as {@code 5s}, {@code 1m_30s}, or {@code 20}
     * @return parsed ticks
     * @throws IllegalArgumentException when the value is blank or invalid
     */
    public static long parseToTicks(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be blank");
        }

        String trimmed = value.trim();
        try {
            long ticks = Long.parseLong(trimmed);
            if (ticks < 0L) {
                throw new IllegalArgumentException("Duration cannot be negative: " + value);
            }
            return ticks;
        } catch (NumberFormatException ignored) {
            // Continue with the formatted duration.
        }

        Matcher matcher = DURATION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }

        long ticks = 0L;
        boolean hasUnit = false;
        for (int group = 1; group <= 7; group++) {
            if (matcher.group(group) != null) {
                hasUnit = true;
                break;
            }
        }
        ticks = add(ticks, matcher.group(1), TICKS_PER_YEAR);
        ticks = add(ticks, matcher.group(2), TICKS_PER_MONTH);
        ticks = add(ticks, matcher.group(3), TICKS_PER_WEEK);
        ticks = add(ticks, matcher.group(4), TICKS_PER_DAY);
        ticks = add(ticks, matcher.group(5), TICKS_PER_HOUR);
        ticks = add(ticks, matcher.group(6), TICKS_PER_MINUTE);
        ticks = add(ticks, matcher.group(7), TICKS_PER_SECOND);

        if (!hasUnit) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }
        return ticks;
    }

    private static long add(long total, String amount, long multiplier) {
        if (amount == null) {
            return total;
        }
        try {
            return Math.addExact(total, Math.multiplyExact(Long.parseLong(amount), multiplier));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Duration is too large", e);
        }
    }
}
