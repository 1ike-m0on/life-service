package io.github.ikemoon.lifeservice.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationScheduler.class);

    private final CacheInvalidationService cacheInvalidationService;
    private final CacheProperties cacheProperties;

    public CacheInvalidationScheduler(
            CacheInvalidationService cacheInvalidationService,
            CacheProperties cacheProperties) {
        this.cacheInvalidationService = cacheInvalidationService;
        this.cacheProperties = cacheProperties;
    }

    @Scheduled(fixedDelayString = "${life-service.cache.invalidation.retry-fixed-delay-ms:60000}")
    public void retryCacheDeleteTasks() {
        if (!cacheProperties.getInvalidation().isEnabled()) {
            return;
        }
        int deleted = cacheInvalidationService.retryPendingTasks();
        if (deleted > 0) {
            log.info("Cache delete retry finished, deleted={}", deleted);
        }
    }
}
