package io.github.ikemoon.lifeservice.order.service.close;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VoucherOrderCloseTxServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    @Mock
    private StockReleaseTaskMapper stockReleaseTaskMapper;

    private VoucherOrderCloseTxService txService;

    @BeforeEach
    void setUp() {
        txService = new VoucherOrderCloseTxService(voucherOrderMapper, stockReleaseTaskMapper);
    }

    @Test
    void closeExpiredOrderUpdatesStatusAndCreatesStockReleaseTask() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(stockReleaseTaskMapper.insert(any(StockReleaseTask.class))).thenReturn(1);

        StockReleaseTask task = txService.closeExpiredOrder(order(), LocalDateTime.now().minusMinutes(15));

        assertThat(task).isNotNull();
        assertThat(task.getOrderId()).isEqualTo(100L);
        assertThat(task.getOrderNo()).isEqualTo("LSO202605200000000001");
        assertThat(task.getVoucherId()).isEqualTo(1L);
        assertThat(task.getUserId()).isEqualTo(10L);
        assertThat(task.getStatus()).isEqualTo(StockReleaseTaskStatus.PENDING.code());
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getNextRetryAt()).isNotNull();

        ArgumentCaptor<StockReleaseTask> taskCaptor = ArgumentCaptor.forClass(StockReleaseTask.class);
        verify(stockReleaseTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getOrderId()).isEqualTo(100L);
    }

    @Test
    void closeExpiredOrderSkipsStockReleaseTaskWhenStatusUpdateLosesRace() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        StockReleaseTask task = txService.closeExpiredOrder(order(), LocalDateTime.now().minusMinutes(15));

        assertThat(task).isNull();
        verify(stockReleaseTaskMapper, never()).insert(any(StockReleaseTask.class));
    }

    private static VoucherOrder order() {
        VoucherOrder order = new VoucherOrder();
        order.setId(100L);
        order.setOrderNo("LSO202605200000000001");
        order.setUserId(10L);
        order.setVoucherId(1L);
        order.setStatus(OrderStatus.PENDING_PAYMENT.code());
        order.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        return order;
    }
}
