package io.github.ikemoon.lifeservice.infrastructure.metrics;

import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMetrics {

    private final MeterRegistry meterRegistry;

    public RateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAllowed(String rule, RateLimitType scope, String endpointGroup) {
        meterRegistry.counter(
                "life.rate.limit.allowed",
                "rule", normalizeRule(rule),
                "scope", normalizeScope(scope),
                "endpoint_group", endpointGroup)
                .increment();
    }

    public void recordRejected(String rule, RateLimitType scope, String endpointGroup) {
        meterRegistry.counter(
                "life.rate.limit.rejected",
                "rule", normalizeRule(rule),
                "scope", normalizeScope(scope),
                "endpoint_group", endpointGroup)
                .increment();
    }

    private static String normalizeRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return "unknown";
        }
        String normalized = rule;
        if (normalized.startsWith("life:rate:")) {
            normalized = normalized.substring("life:rate:".length());
        }
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "unknown" : normalized.replace(':', '_');
    }

    private static String normalizeScope(RateLimitType scope) {
        return scope == null ? "unknown" : scope.name().toLowerCase();
    }
}
