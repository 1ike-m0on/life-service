package io.github.ikemoon.lifeservice.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOrderCloseSuccess() {
        meterRegistry.counter("life.order.close.success").increment();
    }

    public void recordOrderCloseFailure() {
        meterRegistry.counter("life.order.close.failure").increment();
    }

    public void recordStockReleaseFailure() {
        meterRegistry.counter("life.stock.release.failure").increment();
    }

    public void recordStockReleaseRetry() {
        meterRegistry.counter("life.stock.release.retry").increment();
    }

    public void recordPaymentOrderClosed() {
        meterRegistry.counter("life.payment.order.closed").increment();
    }
}
