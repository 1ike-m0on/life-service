package io.github.ikemoon.lifeservice.order.service.close;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.order.service.stock.StockReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VoucherOrderCloseService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderCloseService.class);

    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherOrderCloseTxService closeTxService;
    private final StockReleaseService stockReleaseService;
    private final OrderCloseProperties properties;

    public VoucherOrderCloseService(
            VoucherOrderMapper voucherOrderMapper,
            VoucherOrderCloseTxService closeTxService,
            StockReleaseService stockReleaseService,
            OrderCloseProperties properties) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.closeTxService = closeTxService;
        this.stockReleaseService = stockReleaseService;
        this.properties = properties;
    }

    public OrderCloseSummary closeExpiredOrders() {
        LocalDateTime timeoutPoint = LocalDateTime.now().minus(properties.getPaymentTimeout());
        List<VoucherOrder> orders = voucherOrderMapper.selectList(new QueryWrapper<VoucherOrder>()
                .eq("status", OrderStatus.PENDING_PAYMENT.code())
                .le("created_at", timeoutPoint)
                .orderByAsc("created_at")
                .last("limit " + normalizeBatchSize(properties.getBatchSize())));

        int closed = 0;
        int stockReleased = 0;
        int failed = 0;
        for (VoucherOrder order : orders) {
            try {
                StockReleaseTask task = closeTxService.closeExpiredOrder(order, timeoutPoint);
                if (task == null) {
                    continue;
                }
                closed++;
                if (stockReleaseService.release(task)) {
                    stockReleased++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Failed to close expired order, orderId={}", order.getId(), e);
            }
        }
        return new OrderCloseSummary(orders.size(), closed, stockReleased, failed);
    }

    private static int normalizeBatchSize(int batchSize) {
        return Math.max(1, Math.min(batchSize, 500));
    }
}
