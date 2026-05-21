package io.github.ikemoon.lifeservice.voucher.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "life-service.flash-sale.warmup")
public class FlashSaleVoucherWarmupProperties {

    private boolean enabled = true;
    private boolean failFast = false;
    private int maxVouchers = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public int getMaxVouchers() {
        return maxVouchers;
    }

    public void setMaxVouchers(int maxVouchers) {
        this.maxVouchers = Math.max(0, maxVouchers);
    }
}
