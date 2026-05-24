package io.github.ikemoon.lifeservice.infrastructure.cache;

import io.github.ikemoon.lifeservice.infrastructure.cache.entity.CacheDeleteTask;
import io.github.ikemoon.lifeservice.infrastructure.cache.enums.CacheDeleteTaskStatus;
import io.github.ikemoon.lifeservice.infrastructure.cache.mapper.CacheDeleteTaskMapper;
import io.github.ikemoon.lifeservice.infrastructure.metrics.CacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheInvalidationServiceTest {

    private static final String CACHE_KEY = "life:cache:merchant-category:list";

    private StringRedisTemplate redisTemplate;
    private LocalCacheService localCacheService;
    private CacheDeleteTaskMapper cacheDeleteTaskMapper;
    private CacheProperties cacheProperties;
    private SimpleMeterRegistry meterRegistry;
    private CacheInvalidationService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        localCacheService = mock(LocalCacheService.class);
        cacheDeleteTaskMapper = mock(CacheDeleteTaskMapper.class);
        cacheProperties = new CacheProperties();
        cacheProperties.getInvalidation().setRetryDelay(Duration.ofMinutes(1));
        cacheProperties.getInvalidation().setMaxRetryCount(5);
        meterRegistry = new SimpleMeterRegistry();
        service = new CacheInvalidationService(
                redisTemplate,
                localCacheService,
                cacheDeleteTaskMapper,
                cacheProperties,
                new CacheMetrics(meterRegistry, cacheDeleteTaskMapper));
    }

    @Test
    void invalidateDeletesLocalAndRedisCache() {
        boolean result = service.invalidate(CACHE_KEY, "merchant category updated");

        assertThat(result).isTrue();
        verify(localCacheService).invalidate(CACHE_KEY);
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    void invalidateRecordsTaskWhenRedisDeleteFails() {
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        boolean result = service.invalidate(CACHE_KEY, "merchant category updated");

        assertThat(result).isFalse();
        ArgumentCaptor<CacheDeleteTask> taskCaptor = ArgumentCaptor.forClass(CacheDeleteTask.class);
        verify(cacheDeleteTaskMapper).insert(taskCaptor.capture());
        CacheDeleteTask task = taskCaptor.getValue();
        assertThat(task.getCacheKey()).isEqualTo(CACHE_KEY);
        assertThat(task.getReason()).contains("merchant category updated").contains("redis down");
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getStatus()).isEqualTo(CacheDeleteTaskStatus.PENDING.code());
        assertThat(task.getNextRetryAt()).isNotNull();
        assertThat(counter("life.cache.delete.failure")).isEqualTo(1);
    }

    @Test
    void invalidateAbbreviatesLongFailureReason() {
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));
        String longReason = "x".repeat(300);

        boolean result = service.invalidate(CACHE_KEY, longReason);

        assertThat(result).isFalse();
        ArgumentCaptor<CacheDeleteTask> taskCaptor = ArgumentCaptor.forClass(CacheDeleteTask.class);
        verify(cacheDeleteTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getReason()).hasSize(255);
    }

    @Test
    void retryPendingTasksReturnsZeroWhenNoTaskIsDue() {
        when(cacheDeleteTaskMapper.selectList(any())).thenReturn(List.of());

        int deleted = service.retryPendingTasks();

        assertThat(deleted).isZero();
        verify(localCacheService, never()).invalidate(CACHE_KEY);
        verify(redisTemplate, never()).delete(CACHE_KEY);
    }

    @Test
    void retryPendingTasksMarksSuccessWhenRedisDeleteSucceeds() {
        CacheDeleteTask task = pendingTask(1L, 0);
        when(cacheDeleteTaskMapper.selectList(any())).thenReturn(List.of(task));

        int deleted = service.retryPendingTasks();

        assertThat(deleted).isEqualTo(1);
        verify(localCacheService).invalidate(CACHE_KEY);
        verify(redisTemplate).delete(CACHE_KEY);
        verify(cacheDeleteTaskMapper).update(eq(null), any());
    }

    @Test
    void retryPendingTasksKeepsPendingBeforeMaxRetryCount() {
        CacheDeleteTask task = pendingTask(1L, 3);
        when(cacheDeleteTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        int deleted = service.retryPendingTasks();

        assertThat(deleted).isZero();
        verify(cacheDeleteTaskMapper).update(eq(null), any());
        assertThat(counter("life.cache.delete.task.failed")).isZero();
    }

    @Test
    void retryPendingTasksTreatsNullRetryCountAsFirstRetry() {
        CacheDeleteTask task = pendingTask(1L, 0);
        task.setRetryCount(null);
        when(cacheDeleteTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        int deleted = service.retryPendingTasks();

        assertThat(deleted).isZero();
        verify(cacheDeleteTaskMapper).update(eq(null), any());
        assertThat(counter("life.cache.delete.task.failed")).isZero();
    }

    @Test
    void retryPendingTasksMarksFailedWhenMaxRetryCountReached() {
        CacheDeleteTask task = pendingTask(1L, 4);
        when(cacheDeleteTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));

        int deleted = service.retryPendingTasks();

        assertThat(deleted).isZero();
        verify(cacheDeleteTaskMapper).update(eq(null), any());
        assertThat(counter("life.cache.delete.task.failed")).isEqualTo(1);
    }

    private static CacheDeleteTask pendingTask(Long id, int retryCount) {
        CacheDeleteTask task = new CacheDeleteTask();
        task.setId(id);
        task.setCacheKey(CACHE_KEY);
        task.setRetryCount(retryCount);
        task.setStatus(CacheDeleteTaskStatus.PENDING.code());
        return task;
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }
}
