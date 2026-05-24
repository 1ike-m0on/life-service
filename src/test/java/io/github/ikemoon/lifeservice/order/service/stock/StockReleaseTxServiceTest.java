package io.github.ikemoon.lifeservice.order.service.stock;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class StockReleaseTxServiceTest {

    @Mock
    private FlashSaleVoucherMapper flashSaleVoucherMapper;

    @Mock
    private StockReleaseTaskMapper stockReleaseTaskMapper;

    private StockReleaseTxService txService;

    @BeforeEach
    void setUp() {
        txService = new StockReleaseTxService(flashSaleVoucherMapper, stockReleaseTaskMapper);
    }

    @Test
    void releaseMysqlStockAndMarkSuccessClaimsTaskBeforeReleasingStock() {
        when(stockReleaseTaskMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(flashSaleVoucherMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        txService.releaseMysqlStockAndMarkSuccess(task());

        verify(stockReleaseTaskMapper).update(any(), any(UpdateWrapper.class));
        verify(flashSaleVoucherMapper).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void releaseMysqlStockAndMarkSuccessSkipsWhenTaskAlreadyHandled() {
        when(stockReleaseTaskMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        txService.releaseMysqlStockAndMarkSuccess(task());

        verify(flashSaleVoucherMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void releaseMysqlStockAndMarkSuccessThrowsWhenMysqlStockUpdateFails() {
        when(stockReleaseTaskMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(flashSaleVoucherMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> txService.releaseMysqlStockAndMarkSuccess(task()))
                .withMessage("MySQL flash sale stock release failed");
    }

    private static StockReleaseTask task() {
        StockReleaseTask task = new StockReleaseTask();
        task.setId(1L);
        task.setOrderId(100L);
        task.setVoucherId(1L);
        task.setStatus(StockReleaseTaskStatus.PENDING.code());
        task.setRetryCount(0);
        return task;
    }
}
