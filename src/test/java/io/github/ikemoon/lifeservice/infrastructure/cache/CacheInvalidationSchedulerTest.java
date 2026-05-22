package io.github.ikemoon.lifeservice.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheInvalidationSchedulerTest {

    private CacheInvalidationService cacheInvalidationService;
    private CacheProperties cacheProperties;
    private CacheInvalidationScheduler scheduler;

    @BeforeEach
    void setUp() {
        cacheInvalidationService = mock(CacheInvalidationService.class);
        cacheProperties = new CacheProperties();
        scheduler = new CacheInvalidationScheduler(cacheInvalidationService, cacheProperties);
    }

    @Test
    void retryCacheDeleteTasksSkipsWhenInvalidationIsDisabled() {
        cacheProperties.getInvalidation().setEnabled(false);

        scheduler.retryCacheDeleteTasks();

        verify(cacheInvalidationService, never()).retryPendingTasks();
    }

    @Test
    void retryCacheDeleteTasksRunsWhenInvalidationIsEnabled() {
        when(cacheInvalidationService.retryPendingTasks()).thenReturn(0);

        scheduler.retryCacheDeleteTasks();

        verify(cacheInvalidationService).retryPendingTasks();
    }

    @Test
    void retryCacheDeleteTasksAcceptsPositiveDeleteCount() {
        when(cacheInvalidationService.retryPendingTasks()).thenReturn(2);

        scheduler.retryCacheDeleteTasks();

        verify(cacheInvalidationService).retryPendingTasks();
    }
}
