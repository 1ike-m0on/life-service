package io.github.ikemoon.lifeservice.order.service.close;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.order.service.stock.StockReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VoucherOrderCloseServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    @Mock
    private VoucherOrderCloseTxService closeTxService;

    @Mock
    private StockReleaseService stockReleaseService;

    private VoucherOrderCloseService service;

    @BeforeEach
    void setUp() {
        OrderCloseProperties properties = new OrderCloseProperties();
        properties.setPaymentTimeout(Duration.ofMinutes(15));
        properties.setBatchSize(100);
        service = new VoucherOrderCloseService(voucherOrderMapper, closeTxService, stockReleaseService, properties);
    }

    @Test
    void closeExpiredOrdersClosesAndReleasesStockForScannedOrders() {
        VoucherOrder order = order();
        StockReleaseTask task = task();
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(order));
        when(closeTxService.closeExpiredOrder(any(VoucherOrder.class), any(LocalDateTime.class))).thenReturn(task);
        when(stockReleaseService.release(task)).thenReturn(true);

        OrderCloseSummary summary = service.closeExpiredOrders();

        assertThat(summary.scanned()).isEqualTo(1);
        assertThat(summary.closed()).isEqualTo(1);
        assertThat(summary.stockReleased()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
    }

    @Test
    void closeExpiredOrdersReturnsZeroSummaryWhenNoOrderIsExpired() {
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        OrderCloseSummary summary = service.closeExpiredOrders();

        assertThat(summary.scanned()).isZero();
        assertThat(summary.closed()).isZero();
        assertThat(summary.stockReleased()).isZero();
        assertThat(summary.failed()).isZero();
    }

    @Test
    void closeExpiredOrdersDoesNotReleaseStockWhenCloseLosesRace() {
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(order()));
        when(closeTxService.closeExpiredOrder(any(VoucherOrder.class), any(LocalDateTime.class))).thenReturn(null);

        OrderCloseSummary summary = service.closeExpiredOrders();

        assertThat(summary.scanned()).isEqualTo(1);
        assertThat(summary.closed()).isZero();
        assertThat(summary.stockReleased()).isZero();
        assertThat(summary.failed()).isZero();
    }

    @Test
    void closeExpiredOrdersCountsFailureWhenStockReleaseFails() {
        StockReleaseTask task = task();
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(order()));
        when(closeTxService.closeExpiredOrder(any(VoucherOrder.class), any(LocalDateTime.class))).thenReturn(task);
        when(stockReleaseService.release(task)).thenReturn(false);

        OrderCloseSummary summary = service.closeExpiredOrders();

        assertThat(summary.scanned()).isEqualTo(1);
        assertThat(summary.closed()).isEqualTo(1);
        assertThat(summary.stockReleased()).isZero();
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void closeExpiredOrdersContinuesWhenOneOrderThrows() {
        VoucherOrder first = order();
        VoucherOrder second = order();
        second.setId(101L);
        StockReleaseTask task = task();
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(first, second));
        when(closeTxService.closeExpiredOrder(any(VoucherOrder.class), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("db busy"))
                .thenReturn(task);
        when(stockReleaseService.release(task)).thenReturn(true);

        OrderCloseSummary summary = service.closeExpiredOrders();

        assertThat(summary.scanned()).isEqualTo(2);
        assertThat(summary.closed()).isEqualTo(1);
        assertThat(summary.stockReleased()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
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

    private static StockReleaseTask task() {
        StockReleaseTask task = new StockReleaseTask();
        task.setId(1L);
        task.setOrderId(100L);
        task.setVoucherId(1L);
        task.setStatus(StockReleaseTaskStatus.PENDING.code());
        return task;
    }
}
