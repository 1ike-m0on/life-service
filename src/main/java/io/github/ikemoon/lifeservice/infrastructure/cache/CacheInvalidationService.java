package io.github.ikemoon.lifeservice.infrastructure.cache;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.infrastructure.cache.entity.CacheDeleteTask;
import io.github.ikemoon.lifeservice.infrastructure.cache.enums.CacheDeleteTaskStatus;
import io.github.ikemoon.lifeservice.infrastructure.cache.mapper.CacheDeleteTaskMapper;
import io.github.ikemoon.lifeservice.infrastructure.metrics.CacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final StringRedisTemplate redisTemplate;
    private final LocalCacheService localCacheService;
    private final CacheDeleteTaskMapper cacheDeleteTaskMapper;
    private final CacheProperties cacheProperties;
    private final CacheMetrics cacheMetrics;

    public CacheInvalidationService(
            StringRedisTemplate redisTemplate,
            LocalCacheService localCacheService,
            CacheDeleteTaskMapper cacheDeleteTaskMapper,
            CacheProperties cacheProperties,
            CacheMetrics cacheMetrics) {
        this.redisTemplate = redisTemplate;
        this.localCacheService = localCacheService;
        this.cacheDeleteTaskMapper = cacheDeleteTaskMapper;
        this.cacheProperties = cacheProperties;
        this.cacheMetrics = cacheMetrics;
    }

    public boolean invalidate(String cacheKey, String reason) {
        localCacheService.invalidate(cacheKey);
        try {
            redisTemplate.delete(cacheKey);
            return true;
        } catch (RuntimeException e) {
            recordDeleteTask(cacheKey, reason, e);
            cacheMetrics.recordDeleteFailure();
            log.warn("Cache delete failed and task recorded, cacheKey={}", cacheKey, e);
            return false;
        }
    }

    public int retryPendingTasks() {
        CacheProperties.Invalidation invalidation = cacheProperties.getInvalidation();
        List<CacheDeleteTask> tasks = cacheDeleteTaskMapper.selectList(new QueryWrapper<CacheDeleteTask>()
                .eq("status", CacheDeleteTaskStatus.PENDING.code())
                .le("next_retry_at", LocalDateTime.now())
                .orderByAsc("next_retry_at")
                .last("limit " + normalizeBatchSize(invalidation.getRetryBatchSize())));
        int deleted = 0;
        for (CacheDeleteTask task : tasks) {
            if (retryDelete(task)) {
                deleted++;
            }
        }
        return deleted;
    }

    private boolean retryDelete(CacheDeleteTask task) {
        localCacheService.invalidate(task.getCacheKey());
        try {
            redisTemplate.delete(task.getCacheKey());
            markSuccess(task);
            return true;
        } catch (RuntimeException e) {
            RetryDecision decision = markRetry(task, e);
            if (decision.failed()) {
                cacheMetrics.recordDeleteTaskFinalFailure();
                log.error("Cache delete retry exhausted, taskId={}, cacheKey={}, retryCount={}",
                        task.getId(), task.getCacheKey(), decision.retryCount(), e);
            } else {
                log.warn("Cache delete retry failed, taskId={}, cacheKey={}, retryCount={}",
                        task.getId(), task.getCacheKey(), decision.retryCount(), e);
            }
            return false;
        }
    }

    private void recordDeleteTask(String cacheKey, String reason, RuntimeException error) {
        CacheProperties.Invalidation invalidation = cacheProperties.getInvalidation();
        CacheDeleteTask task = new CacheDeleteTask();
        task.setCacheKey(cacheKey);
        task.setReason(abbreviate(joinReason(reason, error.getMessage())));
        task.setRetryCount(0);
        task.setNextRetryAt(LocalDateTime.now().plus(invalidation.getRetryDelay()));
        task.setStatus(CacheDeleteTaskStatus.PENDING.code());
        cacheDeleteTaskMapper.insert(task);
    }

    private void markSuccess(CacheDeleteTask task) {
        LocalDateTime now = LocalDateTime.now();
        cacheDeleteTaskMapper.update(null, new UpdateWrapper<CacheDeleteTask>()
                .set("status", CacheDeleteTaskStatus.SUCCESS.code())
                .set("updated_at", now)
                .eq("id", task.getId())
                .eq("status", CacheDeleteTaskStatus.PENDING.code()));
    }

    private RetryDecision markRetry(CacheDeleteTask task, RuntimeException error) {
        CacheProperties.Invalidation invalidation = cacheProperties.getInvalidation();
        int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        int nextStatus = nextRetryCount >= invalidation.getMaxRetryCount()
                ? CacheDeleteTaskStatus.FAILED.code()
                : CacheDeleteTaskStatus.PENDING.code();
        LocalDateTime now = LocalDateTime.now();
        cacheDeleteTaskMapper.update(null, new UpdateWrapper<CacheDeleteTask>()
                .set("status", nextStatus)
                .set("reason", abbreviate(error.getMessage()))
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", now.plus(invalidation.getRetryDelay()))
                .set("updated_at", now)
                .eq("id", task.getId())
                .eq("status", CacheDeleteTaskStatus.PENDING.code()));
        return new RetryDecision(nextRetryCount, nextStatus == CacheDeleteTaskStatus.FAILED.code());
    }

    private static int normalizeBatchSize(int batchSize) {
        return Math.max(1, Math.min(batchSize, 500));
    }

    private static String joinReason(String reason, String errorMessage) {
        if (reason == null || reason.isBlank()) {
            return errorMessage;
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            return reason;
        }
        return reason + ": " + errorMessage;
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private record RetryDecision(int retryCount, boolean failed) {
    }
}
