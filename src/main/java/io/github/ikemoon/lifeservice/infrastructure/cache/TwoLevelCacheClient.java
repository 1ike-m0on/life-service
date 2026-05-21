package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Component
public class TwoLevelCacheClient {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LocalCacheService localCacheService;
    private final CacheInvalidationService cacheInvalidationService;

    public TwoLevelCacheClient(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LocalCacheService localCacheService,
            CacheInvalidationService cacheInvalidationService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localCacheService = localCacheService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    public <T> List<T> queryList(
            String key,
            Class<T> elementType,
            Supplier<List<T>> dbFallback,
            Duration redisTtl) {
        List<T> localValue = localCacheService.get(key);
        if (localValue != null) {
            return localValue;
        }

        String cached = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            List<T> redisValue = readList(cached, elementType);
            localCacheService.put(key, redisValue);
            return redisValue;
        }
        if (cached != null) {
            List<T> emptyValue = List.of();
            localCacheService.put(key, emptyValue);
            return emptyValue;
        }

        List<T> dbValue = dbFallback.get();
        List<T> value = dbValue == null ? List.of() : List.copyOf(dbValue);
        writeList(key, value, redisTtl);
        localCacheService.put(key, value);
        return value;
    }

    public void invalidate(String key) {
        cacheInvalidationService.invalidate(key, "two-level cache invalidation");
    }

    private <T> List<T> readList(String json, Class<T> elementType) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return objectMapper.readValue(json, listType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read two-level cache list value", e);
        }
    }

    private void writeList(String key, List<?> value, Duration redisTtl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), redisTtl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write two-level cache list value", e);
        }
    }
}
