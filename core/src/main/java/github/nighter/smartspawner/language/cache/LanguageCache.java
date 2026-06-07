package github.nighter.smartspawner.language.cache;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LanguageCache {
    private final LRUCache<String, String> stringCache;
    private final LRUCache<String, String[]> loreCache;
    private final LRUCache<String, List<String>> loreListCache;
    private final LRUCache<String, String> entityNameCache;
    private final LRUCache<String, String> smallCapsCache;
    private final LRUCache<String, String> materialNameCache;
    private final AtomicInteger cacheHits;
    private final AtomicInteger cacheMisses;

    public LanguageCache(AtomicInteger cacheHits, AtomicInteger cacheMisses) {
        this.stringCache = new LRUCache<>(1000);
        this.loreCache = new LRUCache<>(250);
        this.loreListCache = new LRUCache<>(250);
        this.entityNameCache = new LRUCache<>(250);
        this.smallCapsCache = new LRUCache<>(500);
        this.materialNameCache = new LRUCache<>(250);
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public String getCachedString(String key) {
        String cached = stringCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedString(String key, String value) {
        stringCache.put(key, value);
    }

    public String[] getCachedLore(String key) {
        String[] cached = loreCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedLore(String key, String[] value) {
        loreCache.put(key, value);
    }

    public List<String> getCachedLoreList(String key) {
        List<String> cached = loreListCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedLoreList(String key, List<String> value) {
        loreListCache.put(key, value);
    }

    public String getCachedEntityName(String key) {
        String cached = entityNameCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedEntityName(String key, String value) {
        entityNameCache.put(key, value);
    }

    public String getCachedMaterialName(String key) {
        String cached = materialNameCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedMaterialName(String key, String value) {
        materialNameCache.put(key, value);
    }

    public String getCachedSmallCaps(String key) {
        String cached = smallCapsCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        return null;
    }

    public void putCachedSmallCaps(String key, String value) {
        smallCapsCache.put(key, value);
    }

    public void clear() {
        stringCache.clear();
        loreCache.clear();
        loreListCache.clear();
        entityNameCache.clear();
        smallCapsCache.clear();
        materialNameCache.clear();
    }

    public int getStringCacheSize() {
        return stringCache.size();
    }

    public int getStringCacheCapacity() {
        return stringCache.capacity();
    }

    public int getLoreCacheSize() {
        return loreCache.size();
    }

    public int getLoreCacheCapacity() {
        return loreCache.capacity();
    }

    public int getLoreListCacheSize() {
        return loreListCache.size();
    }

    public int getLoreListCacheCapacity() {
        return loreListCache.capacity();
    }

    public int getEntityNameCacheSize() {
        return entityNameCache.size();
    }

    public int getEntityNameCacheCapacity() {
        return entityNameCache.capacity();
    }

    public int getSmallCapsCacheSize() {
        return smallCapsCache.size();
    }

    public int getSmallCapsCacheCapacity() {
        return smallCapsCache.capacity();
    }

    public int getMaterialNameCacheSize() {
        return materialNameCache.size();
    }

    public int getMaterialNameCacheCapacity() {
        return materialNameCache.capacity();
    }
}
