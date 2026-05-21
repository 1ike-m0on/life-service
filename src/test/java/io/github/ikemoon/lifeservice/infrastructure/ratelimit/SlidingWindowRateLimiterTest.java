package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SlidingWindowRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        rateLimiter = new SlidingWindowRateLimiter(redisTemplate);
    }

    @Test
    void isAllowedReturnsTrueWhenLuaReturnsPositiveCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(1L);

        boolean allowed = rateLimiter.isAllowed("life:rate:test", 1, 10);

        assertThat(allowed).isTrue();
    }

    @Test
    void isAllowedReturnsFalseWhenLuaReturnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(0L);

        boolean allowed = rateLimiter.isAllowed("life:rate:test", 1, 10);

        assertThat(allowed).isFalse();
    }

    @Test
    void isAllowedThrowsWhenLuaReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(null);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> rateLimiter.isAllowed("life:rate:test", 1, 10));
    }

    @Test
    void isAllowedRejectsInvalidConfig() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> rateLimiter.isAllowed("life:rate:test", 0, 10));
    }
}
