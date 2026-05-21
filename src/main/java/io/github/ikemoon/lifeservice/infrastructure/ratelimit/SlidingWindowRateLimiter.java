package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SlidingWindowRateLimiter {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

    static {
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("scripts/sliding_window_rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String key, int windowSeconds, int limit) {
        if (windowSeconds <= 0 || limit <= 0) {
            throw new IllegalArgumentException("Rate limit window and limit must be positive");
        }
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(windowSeconds),
                String.valueOf(limit),
                String.valueOf(System.currentTimeMillis()),
                UUID.randomUUID().toString());
        if (result == null) {
            throw new IllegalStateException("Rate limit check returned null");
        }
        return result > 0;
    }
}
