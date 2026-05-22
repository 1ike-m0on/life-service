package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheClientTest {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private CacheClient cacheClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        cacheClient = new CacheClient(redisTemplate, objectMapper, Runnable::run);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void queryWithPassThroughReturnsCachedValue() throws Exception {
        Merchant cached = merchant(10L, "Coffee Shop");
        when(valueOperations.get("merchant:10")).thenReturn(objectMapper.writeValueAsString(cached));
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        Merchant result = cacheClient.queryWithPassThrough(
                "merchant:",
                10L,
                Merchant.class,
                id -> {
                    fallbackCalled.set(true);
                    return null;
                },
                CACHE_TTL,
                NULL_TTL);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Coffee Shop");
        assertThat(fallbackCalled).isFalse();
        verify(valueOperations, never()).set(anyString(), anyString(), eq(CACHE_TTL));
    }

    @Test
    void getReturnsCachedValue() throws Exception {
        Merchant cached = merchant(10L, "Coffee Shop");
        when(valueOperations.get("merchant:10")).thenReturn(objectMapper.writeValueAsString(cached));

        Merchant result = cacheClient.get("merchant:10", Merchant.class);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Coffee Shop");
    }

    @Test
    void getReturnsNullWhenKeyMissing() {
        when(valueOperations.get("merchant:10")).thenReturn(null);

        Merchant result = cacheClient.get("merchant:10", Merchant.class);

        assertThat(result).isNull();
    }

    @Test
    void queryWithPassThroughReturnsNullWhenNullSentinelHit() {
        when(valueOperations.get("merchant:10")).thenReturn("");
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        Merchant result = cacheClient.queryWithPassThrough(
                "merchant:",
                10L,
                Merchant.class,
                id -> {
                    fallbackCalled.set(true);
                    return merchant(10L, "Coffee Shop");
                },
                CACHE_TTL,
                NULL_TTL);

        assertThat(result).isNull();
        assertThat(fallbackCalled).isFalse();
    }

    @Test
    void queryWithPassThroughWritesNullSentinelWhenDatabaseMisses() {
        when(valueOperations.get("merchant:10")).thenReturn(null);

        Merchant result = cacheClient.queryWithPassThrough(
                "merchant:",
                10L,
                Merchant.class,
                id -> null,
                CACHE_TTL,
                NULL_TTL);

        assertThat(result).isNull();
        verify(valueOperations).set("merchant:10", "", NULL_TTL);
    }

    @Test
    void queryWithPassThroughWritesDatabaseValueWhenCacheMisses() {
        when(valueOperations.get("merchant:10")).thenReturn(null);

        Merchant result = cacheClient.queryWithPassThrough(
                "merchant:",
                10L,
                Merchant.class,
                id -> merchant(10L, "Coffee Shop"),
                CACHE_TTL,
                NULL_TTL);

        assertThat(result.getName()).isEqualTo("Coffee Shop");
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("merchant:10"), jsonCaptor.capture(), eq(CACHE_TTL));
        assertThat(jsonCaptor.getValue()).contains("Coffee Shop");
    }

    @Test
    void queryWithLogicalExpireReturnsStaleValueAndRebuildsWhenExpired() throws Exception {
        Merchant stale = merchant(10L, "Old Coffee Shop");
        String payload = objectMapper.writeValueAsString(Map.of(
                "data", stale,
                "expireAt", LocalDateTime.now().minusSeconds(1).toString()));
        when(valueOperations.get("merchant:10")).thenReturn(payload);
        when(valueOperations.setIfAbsent("lock:merchant:10", "1", Duration.ofSeconds(10))).thenReturn(true);

        Merchant result = cacheClient.queryWithLogicalExpire(
                "merchant:",
                "lock:merchant:",
                10L,
                Merchant.class,
                id -> merchant(10L, "Fresh Coffee Shop"),
                CACHE_TTL,
                Duration.ofSeconds(10));

        assertThat(result.getName()).isEqualTo("Old Coffee Shop");
        ArgumentCaptor<String> rebuiltJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("merchant:10"), rebuiltJsonCaptor.capture());
        assertThat(rebuiltJsonCaptor.getValue()).contains("Fresh Coffee Shop");
        verify(redisTemplate).delete("lock:merchant:10");
    }

    @Test
    void queryWithLogicalExpireReturnsNullWhenCacheIsMissing() {
        when(valueOperations.get("merchant:10")).thenReturn(null);

        Merchant result = cacheClient.queryWithLogicalExpire(
                "merchant:",
                "lock:merchant:",
                10L,
                Merchant.class,
                id -> merchant(10L, "Fresh Coffee Shop"),
                CACHE_TTL,
                Duration.ofSeconds(10));

        assertThat(result).isNull();
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10)));
    }

    @Test
    void queryWithLogicalExpireReturnsFreshValueWithoutRebuildWhenNotExpired() throws Exception {
        Merchant fresh = merchant(10L, "Fresh Coffee Shop");
        String payload = objectMapper.writeValueAsString(Map.of(
                "data", fresh,
                "expireAt", LocalDateTime.now().plusMinutes(5).toString()));
        when(valueOperations.get("merchant:10")).thenReturn(payload);

        Merchant result = cacheClient.queryWithLogicalExpire(
                "merchant:",
                "lock:merchant:",
                10L,
                Merchant.class,
                id -> merchant(10L, "Unexpected Rebuild"),
                CACHE_TTL,
                Duration.ofSeconds(10));

        assertThat(result.getName()).isEqualTo("Fresh Coffee Shop");
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10)));
    }

    @Test
    void queryWithLogicalExpireReturnsStaleValueWithoutRebuildWhenLockIsBusy() throws Exception {
        Merchant stale = merchant(10L, "Old Coffee Shop");
        String payload = objectMapper.writeValueAsString(Map.of(
                "data", stale,
                "expireAt", LocalDateTime.now().minusSeconds(1).toString()));
        when(valueOperations.get("merchant:10")).thenReturn(payload);
        when(valueOperations.setIfAbsent("lock:merchant:10", "1", Duration.ofSeconds(10))).thenReturn(false);

        Merchant result = cacheClient.queryWithLogicalExpire(
                "merchant:",
                "lock:merchant:",
                10L,
                Merchant.class,
                id -> merchant(10L, "Unexpected Rebuild"),
                CACHE_TTL,
                Duration.ofSeconds(10));

        assertThat(result.getName()).isEqualTo("Old Coffee Shop");
        verify(valueOperations, never()).set(eq("merchant:10"), anyString());
        verify(redisTemplate, never()).delete("lock:merchant:10");
    }

    private static Merchant merchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName(name);
        merchant.setStatus(1);
        return merchant;
    }
}
