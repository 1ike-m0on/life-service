package io.github.ikemoon.lifeservice.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleMetrics {

    private final MeterRegistry meterRegistry;

    public FlashSaleMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest() {
        meterRegistry.counter("life.flash.sale.request").increment();
    }

    public void recordSuccess() {
        meterRegistry.counter("life.flash.sale.success").increment();
    }

    public void recordStockNotEnough() {
        meterRegistry.counter("life.flash.sale.stock.not.enough").increment();
    }

    public void recordDuplicate() {
        meterRegistry.counter("life.flash.sale.duplicate").increment();
    }

    public void recordNotReady() {
        meterRegistry.counter("life.flash.sale.not.ready").increment();
    }

    public void recordMqPublishFailure() {
        meterRegistry.counter("life.flash.sale.mq.publish.failure").increment();
    }

    public void recordRedisRollbackFailure() {
        meterRegistry.counter("life.flash.sale.redis.rollback.failure").increment();
    }
}
