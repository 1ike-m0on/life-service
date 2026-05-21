package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Component;

@Component
public class LocalCacheService {

    private final Cache<String, Object> localCache;
    private final CacheProperties cacheProperties;

    public LocalCacheService(Cache<String, Object> localCache, CacheProperties cacheProperties) {
        this.localCache = localCache;
        this.cacheProperties = cacheProperties;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!cacheProperties.getLocal().isEnabled()) {
            return null;
        }
        return (T) localCache.getIfPresent(key);
    }

    public void put(String key, Object value) {
        if (!cacheProperties.getLocal().isEnabled() || value == null) {
            return;
        }
        localCache.put(key, value);
    }

    public void invalidate(String key) {
        localCache.invalidate(key);
    }
}
