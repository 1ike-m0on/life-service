package io.github.ikemoon.lifeservice.order.service.stock;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StockReleaseTxService {

    private final FlashSaleVoucherMapper flashSaleVoucherMapper;
    private final StockReleaseTaskMapper stockReleaseTaskMapper;

    public StockReleaseTxService(
            FlashSaleVoucherMapper flashSaleVoucherMapper,
            StockReleaseTaskMapper stockReleaseTaskMapper) {
        this.flashSaleVoucherMapper = flashSaleVoucherMapper;
        this.stockReleaseTaskMapper = stockReleaseTaskMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseMysqlStockAndMarkSuccess(StockReleaseTask task) {
        boolean claimed = stockReleaseTaskMapper.update(null, new UpdateWrapper<StockReleaseTask>()
                .set("status", StockReleaseTaskStatus.SUCCESS.code())
                .set("reason", "released")
                .set("updated_at", LocalDateTime.now())
                .eq("id", task.getId())
                .eq("status", StockReleaseTaskStatus.PENDING.code())) == 1;
        if (!claimed) {
            return;
        }

        boolean stockReleased = flashSaleVoucherMapper.update(null, new UpdateWrapper<FlashSaleVoucher>()
                .setSql("stock = stock + 1")
                .eq("voucher_id", task.getVoucherId())) == 1;
        if (!stockReleased) {
            throw new IllegalStateException("MySQL flash sale stock release failed");
        }
    }
}
