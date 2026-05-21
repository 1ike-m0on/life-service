package io.github.ikemoon.lifeservice.order.service.close;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "life-service.order.close")
public class OrderCloseProperties {

    private boolean enabled = true;
    private Duration paymentTimeout = Duration.ofMinutes(15);
    private int batchSize = 200;
    private Duration stockReleaseRetryDelay = Duration.ofMinutes(1);
    private int stockReleaseMaxRetryCount = 5;
    private int stockReleaseRetryBatchSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getPaymentTimeout() {
        return paymentTimeout;
    }

    public void setPaymentTimeout(Duration paymentTimeout) {
        this.paymentTimeout = paymentTimeout;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getStockReleaseRetryDelay() {
        return stockReleaseRetryDelay;
    }

    public void setStockReleaseRetryDelay(Duration stockReleaseRetryDelay) {
        this.stockReleaseRetryDelay = stockReleaseRetryDelay;
    }

    public int getStockReleaseMaxRetryCount() {
        return stockReleaseMaxRetryCount;
    }

    public void setStockReleaseMaxRetryCount(int stockReleaseMaxRetryCount) {
        this.stockReleaseMaxRetryCount = stockReleaseMaxRetryCount;
    }

    public int getStockReleaseRetryBatchSize() {
        return stockReleaseRetryBatchSize;
    }

    public void setStockReleaseRetryBatchSize(int stockReleaseRetryBatchSize) {
        this.stockReleaseRetryBatchSize = stockReleaseRetryBatchSize;
    }
}
