package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Component
public class CacheClient {

    private static final Logger log = LoggerFactory.getLogger(CacheClient.class);
    private static final String NULL_VALUE = "";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor cacheRebuildExecutor;

    public CacheClient(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("applicationTaskExecutor") Executor cacheRebuildExecutor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheRebuildExecutor = cacheRebuildExecutor;
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write cache value", e);
        }
    }

    public void setWithLogicalExpire(String key, Object value, Duration logicalTtl) {
        LogicalPayload payload = new LogicalPayload(value, LocalDateTime.now().plus(logicalTtl).toString());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write logical expire cache value", e);
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public <T, ID> T queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<T> type,
            Function<ID, T> dbFallback,
            Duration ttl,
            Duration nullTtl) {
        String key = keyPrefix + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            return readValue(cached, type);
        }
        if (cached != null) {
            return null;
        }

        T value = dbFallback.apply(id);
        if (value == null) {
            redisTemplate.opsForValue().set(key, NULL_VALUE, nullTtl);
            return null;
        }

        set(key, value, ttl);
        return value;
    }

    public <T, ID> T queryWithLogicalExpire(
            String keyPrefix,
            String lockKeyPrefix,
            ID id,
            Class<T> type,
            Function<ID, T> dbFallback,
            Duration logicalTtl,
            Duration lockTtl) {
        String key = keyPrefix + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached)) {
            return null;
        }

        LogicalValue<T> logicalValue = readLogicalValue(cached, type);
        if (logicalValue.expireAt().isAfter(LocalDateTime.now())) {
            return logicalValue.data();
        }

        String lockKey = lockKeyPrefix + id;
        if (tryLock(lockKey, lockTtl)) {
            cacheRebuildExecutor.execute(() -> {
                try {
                    T freshValue = dbFallback.apply(id);
                    if (freshValue == null) {
                        delete(key);
                    } else {
                        setWithLogicalExpire(key, freshValue, logicalTtl);
                    }
                } catch (Exception e) {
                    log.warn("Cache rebuild failed, key={}", key, e);
                } finally {
                    delete(lockKey);
                }
            });
        }
        return logicalValue.data();
    }

    private boolean tryLock(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
    }

    private <T> T readValue(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read cache value", e);
        }
    }

    private <T> LogicalValue<T> readLogicalValue(String json, Class<T> type) {
        try {
            JsonNode root = objectMapper.readTree(json);
            T data = objectMapper.treeToValue(root.get("data"), type);
            LocalDateTime expireAt = LocalDateTime.parse(root.get("expireAt").asText());
            return new LogicalValue<>(data, expireAt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read logical expire cache value", e);
        }
    }

    private record LogicalPayload(Object data, String expireAt) {
    }

    private record LogicalValue<T>(T data, LocalDateTime expireAt) {
    }
}
