package io.github.ikemoon.lifeservice.order.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.metrics.OrderMetrics;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VoucherOrderPaymentServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    private SimpleMeterRegistry meterRegistry;

    private VoucherOrderPaymentService paymentService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentService = new VoucherOrderPaymentService(voucherOrderMapper, new OrderMetrics(meterRegistry));
    }

    @Test
    void payUpdatesPendingOrderToPaid() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        VoucherOrderPaymentResult result = paymentService.pay("LSO202605200000000001", 10L);

        assertThat(result.success()).isTrue();
        assertThat(result.orderNo()).isEqualTo("LSO202605200000000001");
        assertThat(result.status()).isEqualTo(OrderStatus.PAID.code());
        assertThat(result.idempotent()).isFalse();
        verify(voucherOrderMapper, never()).selectOne(any(QueryWrapper.class));
    }

    @Test
    void payIsIdempotentWhenOrderAlreadyPaid() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        when(voucherOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(order(OrderStatus.PAID));

        VoucherOrderPaymentResult result = paymentService.pay("LSO202605200000000001", 10L);

        assertThat(result.success()).isTrue();
        assertThat(result.orderNo()).isEqualTo("LSO202605200000000001");
        assertThat(result.status()).isEqualTo(OrderStatus.PAID.code());
        assertThat(result.idempotent()).isTrue();
    }

    @Test
    void payFailsWhenCloseAlreadyWonRace() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        when(voucherOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(order(OrderStatus.CLOSED));

        VoucherOrderPaymentResult result = paymentService.pay("LSO202605200000000001", 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.ORDER_CLOSED);
        assertThat(counter("life.payment.order.closed")).isEqualTo(1);
    }

    @Test
    void payReturnsNotFoundWhenOrderDoesNotExist() {
        when(voucherOrderMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        when(voucherOrderMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        VoucherOrderPaymentResult result = paymentService.pay("LSO202605200000000001", 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void payReturnsBadRequestWhenUserIdMissing() {
        VoucherOrderPaymentResult result = paymentService.pay("LSO202605200000000001", null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
        verify(voucherOrderMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    private static VoucherOrder order(OrderStatus status) {
        VoucherOrder order = new VoucherOrder();
        order.setOrderNo("LSO202605200000000001");
        order.setUserId(10L);
        order.setStatus(status.code());
        return order;
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }
}
