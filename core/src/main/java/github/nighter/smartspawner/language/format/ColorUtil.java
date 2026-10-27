package github.nighter.smartspawner.language.format;

/** Kept for callers outside {@code language/}: formats MiniMessage and legacy text through {@link TextFormatter}. */
public class ColorUtil {

    public static String translateHexColorCodes(String message) {
        return TextFormatter.toLegacy(message);
    }
}
