package io.github.ikemoon.lifeservice.order.service.close;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderCloseTxService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final StockReleaseTaskMapper stockReleaseTaskMapper;

    public VoucherOrderCloseTxService(
            VoucherOrderMapper voucherOrderMapper,
            StockReleaseTaskMapper stockReleaseTaskMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.stockReleaseTaskMapper = stockReleaseTaskMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockReleaseTask closeExpiredOrder(VoucherOrder order, LocalDateTime timeoutPoint) {
        LocalDateTime now = LocalDateTime.now();
        boolean closed = voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", OrderStatus.CLOSED.code())
                .set("closed_at", now)
                .set("updated_at", now)
                .eq("id", order.getId())
                .eq("status", OrderStatus.PENDING_PAYMENT.code())
                .le("created_at", timeoutPoint)) == 1;
        if (!closed) {
            return null;
        }

        StockReleaseTask task = buildStockReleaseTask(order, now);
        stockReleaseTaskMapper.insert(task);
        return task;
    }

    private StockReleaseTask buildStockReleaseTask(VoucherOrder order, LocalDateTime now) {
        StockReleaseTask task = new StockReleaseTask();
        task.setOrderId(order.getId());
        task.setOrderNo(order.getOrderNo());
        task.setVoucherId(order.getVoucherId());
        task.setUserId(order.getUserId());
        task.setReason("order closed");
        task.setRetryCount(0);
        task.setNextRetryAt(now);
        task.setStatus(StockReleaseTaskStatus.PENDING.code());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
