package github.nighter.smartspawner.language.format;

import github.nighter.smartspawner.language.cache.LanguageCache;

import java.util.concurrent.atomic.AtomicInteger;

public final class SmallCapsFormatter {
    private final LanguageCache cache;
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;

    public SmallCapsFormatter(LanguageCache cache, AtomicInteger cacheHits, AtomicInteger cacheMisses) {
        this.cache = cache;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cacheKey = "smallcaps|" + text;
        String cached = cache.getCachedSmallCaps(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                char lowercaseChar = Character.toLowerCase(c);
                char smallCapsChar = getSmallCapsChar(lowercaseChar);
                result.append(smallCapsChar);
            } else {
                result.append(c);
            }
        }

        String smallCapsText = result.toString();
        cache.putCachedSmallCaps(cacheKey, smallCapsText);
        return smallCapsText;
    }

    private char getSmallCapsChar(char c) {
        return switch (c) {
            case 'a' -> 'ᴀ';
            case 'b' -> 'ʙ';
            case 'c' -> 'ᴄ';
            case 'd' -> 'ᴅ';
            case 'e' -> 'ᴇ';
            case 'f' -> 'ꜰ';
            case 'g' -> 'ɢ';
            case 'h' -> 'ʜ';
            case 'i' -> 'ɪ';
            case 'j' -> 'ᴊ';
            case 'k' -> 'ᴋ';
            case 'l' -> 'ʟ';
            case 'm' -> 'ᴍ';
            case 'n' -> 'ɴ';
            case 'o' -> 'ᴏ';
            case 'p' -> 'ᴘ';
            case 'q' -> 'ǫ';
            case 'r' -> 'ʀ';
            case 's' -> 'ꜱ';
            case 't' -> 'ᴛ';
            case 'u' -> 'ᴜ';
            case 'v' -> 'ᴠ';
            case 'w' -> 'ᴡ';
            case 'x' -> 'x';
            case 'y' -> 'ʏ';
            case 'z' -> 'ᴢ';
            default -> c;
        };
    }
}
