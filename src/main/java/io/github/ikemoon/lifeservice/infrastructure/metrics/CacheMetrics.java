package io.github.ikemoon.lifeservice.infrastructure.metrics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.infrastructure.cache.entity.CacheDeleteTask;
import io.github.ikemoon.lifeservice.infrastructure.cache.enums.CacheDeleteTaskStatus;
import io.github.ikemoon.lifeservice.infrastructure.cache.mapper.CacheDeleteTaskMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CacheMetrics {

    private final MeterRegistry meterRegistry;
    private final CacheDeleteTaskMapper cacheDeleteTaskMapper;

    public CacheMetrics(MeterRegistry meterRegistry, CacheDeleteTaskMapper cacheDeleteTaskMapper) {
        this.meterRegistry = meterRegistry;
        this.cacheDeleteTaskMapper = cacheDeleteTaskMapper;
        Gauge.builder("life.cache.delete.task.pending", this, CacheMetrics::pendingDeleteTasks)
                .description("Pending cache delete retry tasks")
                .register(meterRegistry);
    }

    public void recordDeleteFailure() {
        meterRegistry.counter("life.cache.delete.failure").increment();
    }

    public void recordDeleteTaskFinalFailure() {
        meterRegistry.counter("life.cache.delete.task.failed").increment();
    }

    private double pendingDeleteTasks() {
        try {
            Long count = cacheDeleteTaskMapper.selectCount(new QueryWrapper<CacheDeleteTask>()
                    .eq("status", CacheDeleteTaskStatus.PENDING.code()));
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
