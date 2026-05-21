package io.github.ikemoon.lifeservice.order.service.close;

import io.github.ikemoon.lifeservice.order.service.stock.StockReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseScheduler.class);

    private final VoucherOrderCloseService closeService;
    private final StockReleaseService stockReleaseService;
    private final OrderCloseProperties properties;

    public OrderCloseScheduler(
            VoucherOrderCloseService closeService,
            StockReleaseService stockReleaseService,
            OrderCloseProperties properties) {
        this.closeService = closeService;
        this.stockReleaseService = stockReleaseService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${life-service.order.close.scan-fixed-delay-ms:60000}")
    public void closeExpiredOrders() {
        if (!properties.isEnabled()) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        OrderCloseSummary summary = closeService.closeExpiredOrders();
        log.info("Expired order close finished, scanned={}, closed={}, stockReleased={}, failed={}, durationMs={}",
                summary.scanned(), summary.closed(), summary.stockReleased(), summary.failed(),
                System.currentTimeMillis() - startedAt);
    }

    @Scheduled(fixedDelayString = "${life-service.order.close.stock-release-retry-fixed-delay-ms:60000}")
    public void retryStockReleaseTasks() {
        if (!properties.isEnabled()) {
            return;
        }
        int released = stockReleaseService.retryPendingTasks();
        if (released > 0) {
            log.info("Stock release retry finished, released={}", released);
        }
    }
}
