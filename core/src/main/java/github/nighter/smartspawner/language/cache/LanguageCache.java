package github.nighter.smartspawner.language.cache;

import github.nighter.smartspawner.utils.LRUCache;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** The parsed-text caches of one locale. Components are immutable, so a cached one is shared safely. */
public final class LanguageCache {
    private final LRUCache<String, Component> textCache = new LRUCache<>(1000);
    private final LRUCache<String, Component> chatCache = new LRUCache<>(500);
    private final LRUCache<String, String> entityNameCache = new LRUCache<>(250);
    private final LRUCache<String, String> smallCapsCache = new LRUCache<>(500);
    private final LRUCache<String, String> materialNameCache = new LRUCache<>(250);
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;

    public LanguageCache(AtomicInteger cacheHits, AtomicInteger cacheMisses) {
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public Component getText(String key) {
        return count(textCache.get(key));
    }

    public void putText(String key, Component value) {
        textCache.put(key, value);
    }

    public Component getChat(String key) {
        return count(chatCache.get(key));
    }

    public void putChat(String key, Component value) {
        chatCache.put(key, value);
    }

    public String getCachedEntityName(String key) {
        return count(entityNameCache.get(key));
    }

    public void putCachedEntityName(String key, String value) {
        entityNameCache.put(key, value);
    }

    public String getCachedMaterialName(String key) {
        return count(materialNameCache.get(key));
    }

    public void putCachedMaterialName(String key, String value) {
        materialNameCache.put(key, value);
    }

    public String getCachedSmallCaps(String key) {
        return count(smallCapsCache.get(key));
    }

    public void putCachedSmallCaps(String key, String value) {
        smallCapsCache.put(key, value);
    }

    public void clear() {
        textCache.clear();
        chatCache.clear();
        entityNameCache.clear();
        smallCapsCache.clear();
        materialNameCache.clear();
    }

    public void addStats(Map<String, Object> stats, String prefix) {
        stats.put(prefix + "text_cache_size", textCache.size());
        stats.put(prefix + "text_cache_capacity", textCache.capacity());
        stats.put(prefix + "chat_cache_size", chatCache.size());
        stats.put(prefix + "chat_cache_capacity", chatCache.capacity());
        stats.put(prefix + "entity_name_cache_size", entityNameCache.size());
        stats.put(prefix + "small_caps_cache_size", smallCapsCache.size());
        stats.put(prefix + "material_name_cache_size", materialNameCache.size());
    }

    private <T> T count(T cached) {
        if (cached != null) cacheHits.incrementAndGet();
        else cacheMisses.incrementAndGet();
        return cached;
    }
}
