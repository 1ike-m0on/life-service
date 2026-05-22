package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalCacheServiceTest {

    private Cache<String, Object> localCache;
    private CacheProperties cacheProperties;
    private LocalCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        localCache = mock(Cache.class);
        cacheProperties = new CacheProperties();
        service = new LocalCacheService(localCache, cacheProperties);
    }

    @Test
    void getReturnsCachedValueWhenLocalCacheIsEnabled() {
        when(localCache.getIfPresent("merchant:1")).thenReturn("cached");

        String value = service.get("merchant:1");

        assertThat(value).isEqualTo("cached");
    }

    @Test
    void getReturnsNullWithoutTouchingCacheWhenLocalCacheIsDisabled() {
        cacheProperties.getLocal().setEnabled(false);

        String value = service.get("merchant:1");

        assertThat(value).isNull();
        verifyNoInteractions(localCache);
    }

    @Test
    void putWritesValueWhenLocalCacheIsEnabled() {
        service.put("merchant:1", "cached");

        verify(localCache).put("merchant:1", "cached");
    }

    @Test
    void putSkipsWhenLocalCacheIsDisabled() {
        cacheProperties.getLocal().setEnabled(false);

        service.put("merchant:1", "cached");

        verify(localCache, never()).put("merchant:1", "cached");
    }

    @Test
    void putSkipsNullValue() {
        service.put("merchant:1", null);

        verify(localCache, never()).put("merchant:1", null);
    }

    @Test
    void invalidateAlwaysInvalidatesLocalKey() {
        service.invalidate("merchant:1");

        verify(localCache).invalidate("merchant:1");
    }
}
