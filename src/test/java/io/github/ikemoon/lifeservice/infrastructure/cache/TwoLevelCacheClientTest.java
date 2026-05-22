package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwoLevelCacheClientTest {

    private static final String CACHE_KEY = "merchant-category:list";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LocalCacheService localCacheService;
    private CacheInvalidationService cacheInvalidationService;
    private ObjectMapper objectMapper;
    private TwoLevelCacheClient cacheClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        localCacheService = mock(LocalCacheService.class);
        cacheInvalidationService = mock(CacheInvalidationService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        cacheClient = new TwoLevelCacheClient(redisTemplate, objectMapper, localCacheService, cacheInvalidationService);
    }

    @Test
    void queryListReturnsLocalValueWithoutTouchingRedis() {
        MerchantCategory food = category(1L, "Food");
        when(localCacheService.<List<MerchantCategory>>get(CACHE_KEY)).thenReturn(List.of(food));

        List<MerchantCategory> result = cacheClient.queryList(
                CACHE_KEY,
                MerchantCategory.class,
                () -> {
                    throw new AssertionError("Database fallback should not be called");
                },
                REDIS_TTL);

        assertThat(result).containsExactly(food);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void queryListReturnsRedisValueAndBackfillsLocalCache() throws Exception {
        MerchantCategory food = category(1L, "Food");
        when(localCacheService.<List<MerchantCategory>>get(CACHE_KEY)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(objectMapper.writeValueAsString(List.of(food)));
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        List<MerchantCategory> result = cacheClient.queryList(
                CACHE_KEY,
                MerchantCategory.class,
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                },
                REDIS_TTL);

        assertThat(result).extracting(MerchantCategory::getName).containsExactly("Food");
        assertThat(fallbackCalled).isFalse();
        verify(localCacheService).put(eq(CACHE_KEY), eq(result));
    }

    @Test
    void queryListLoadsDatabaseAndBackfillsBothCachesWhenMisses() {
        MerchantCategory food = category(1L, "Food");
        when(localCacheService.<List<MerchantCategory>>get(CACHE_KEY)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        List<MerchantCategory> result = cacheClient.queryList(
                CACHE_KEY,
                MerchantCategory.class,
                () -> List.of(food),
                REDIS_TTL);

        assertThat(result).containsExactly(food);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(CACHE_KEY), jsonCaptor.capture(), eq(REDIS_TTL));
        assertThat(jsonCaptor.getValue()).contains("Food");
        verify(localCacheService).put(eq(CACHE_KEY), eq(result));
    }

    @Test
    void queryListReturnsEmptyValueWhenRedisNullSentinelHit() {
        when(localCacheService.<List<MerchantCategory>>get(CACHE_KEY)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn("");
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        List<MerchantCategory> result = cacheClient.queryList(
                CACHE_KEY,
                MerchantCategory.class,
                () -> {
                    fallbackCalled.set(true);
                    return List.of(category(1L, "Food"));
                },
                REDIS_TTL);

        assertThat(result).isEmpty();
        assertThat(fallbackCalled).isFalse();
        verify(localCacheService).put(CACHE_KEY, List.of());
    }

    @Test
    void queryListWritesEmptyListWhenDatabaseReturnsNull() {
        when(localCacheService.<List<MerchantCategory>>get(CACHE_KEY)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        List<MerchantCategory> result = cacheClient.queryList(
                CACHE_KEY,
                MerchantCategory.class,
                () -> null,
                REDIS_TTL);

        assertThat(result).isEmpty();
        verify(valueOperations).set(CACHE_KEY, "[]", REDIS_TTL);
        verify(localCacheService).put(CACHE_KEY, List.of());
    }

    @Test
    void invalidateDeletesLocalAndRedisCache() {
        cacheClient.invalidate(CACHE_KEY);

        verify(cacheInvalidationService).invalidate(CACHE_KEY, "two-level cache invalidation");
    }

    private static MerchantCategory category(Long id, String name) {
        MerchantCategory category = new MerchantCategory();
        category.setId(id);
        category.setName(name);
        category.setStatus(1);
        return category;
    }
}
