package io.github.ikemoon.lifeservice.order.service.close;

import io.github.ikemoon.lifeservice.order.service.stock.StockReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCloseSchedulerTest {

    private VoucherOrderCloseService closeService;
    private StockReleaseService stockReleaseService;
    private OrderCloseProperties properties;
    private OrderCloseScheduler scheduler;

    @BeforeEach
    void setUp() {
        closeService = mock(VoucherOrderCloseService.class);
        stockReleaseService = mock(StockReleaseService.class);
        properties = new OrderCloseProperties();
        scheduler = new OrderCloseScheduler(closeService, stockReleaseService, properties);
    }

    @Test
    void closeExpiredOrdersSkipsWhenSchedulerIsDisabled() {
        properties.setEnabled(false);

        scheduler.closeExpiredOrders();

        verify(closeService, never()).closeExpiredOrders();
    }

    @Test
    void closeExpiredOrdersRunsWhenSchedulerIsEnabled() {
        when(closeService.closeExpiredOrders()).thenReturn(new OrderCloseSummary(1, 1, 1, 0));

        scheduler.closeExpiredOrders();

        verify(closeService).closeExpiredOrders();
    }

    @Test
    void retryStockReleaseTasksSkipsWhenSchedulerIsDisabled() {
        properties.setEnabled(false);

        scheduler.retryStockReleaseTasks();

        verify(stockReleaseService, never()).retryPendingTasks();
    }

    @Test
    void retryStockReleaseTasksRunsWhenSchedulerIsEnabled() {
        when(stockReleaseService.retryPendingTasks()).thenReturn(0);

        scheduler.retryStockReleaseTasks();

        verify(stockReleaseService).retryPendingTasks();
    }

    @Test
    void retryStockReleaseTasksAcceptsPositiveReleaseCount() {
        when(stockReleaseService.retryPendingTasks()).thenReturn(2);

        scheduler.retryStockReleaseTasks();

        verify(stockReleaseService).retryPendingTasks();
    }
}
