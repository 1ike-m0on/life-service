package io.github.ikemoon.lifeservice.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "life-service.cache")
public class CacheProperties {

    private Local local = new Local();
    private Invalidation invalidation = new Invalidation();

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Invalidation getInvalidation() {
        return invalidation;
    }

    public void setInvalidation(Invalidation invalidation) {
        this.invalidation = invalidation;
    }

    public static class Local {

        private boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(5);
        private long maximumSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    public static class Invalidation {

        private boolean enabled = true;
        private Duration retryDelay = Duration.ofMinutes(1);
        private int maxRetryCount = 5;
        private int retryBatchSize = 200;
        private long retryFixedDelayMs = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public void setMaxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }

        public int getRetryBatchSize() {
            return retryBatchSize;
        }

        public void setRetryBatchSize(int retryBatchSize) {
            this.retryBatchSize = retryBatchSize;
        }

        public long getRetryFixedDelayMs() {
            return retryFixedDelayMs;
        }

        public void setRetryFixedDelayMs(long retryFixedDelayMs) {
            this.retryFixedDelayMs = retryFixedDelayMs;
        }
    }
}
