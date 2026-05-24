package io.github.ikemoon.lifeservice.order.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class FlashSaleOrderCommandHandlerTest {

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private FlashSaleVoucherMapper flashSaleVoucherMapper;

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    private FlashSaleOrderCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FlashSaleOrderCommandHandler(voucherMapper, flashSaleVoucherMapper, voucherOrderMapper);
    }

    @Test
    void handleCreatesPendingOrderAndDeductsStock() {
        when(voucherOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(voucherMapper.selectById(1L)).thenReturn(voucher());
        when(voucherOrderMapper.insert(any(VoucherOrder.class))).thenReturn(1);
        when(flashSaleVoucherMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        handler.handle(new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L));

        ArgumentCaptor<VoucherOrder> orderCaptor = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(voucherOrderMapper).insert(orderCaptor.capture());
        VoucherOrder order = orderCaptor.getValue();
        assertThat(order.getOrderNo()).isEqualTo("LSO202605200000000001");
        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getVoucherId()).isEqualTo(1L);
        assertThat(order.getMerchantId()).isEqualTo(100L);
        assertThat(order.getPayAmountCent()).isEqualTo(990L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.code());
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
        verify(flashSaleVoucherMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void handleIgnoresDuplicateCommand() {
        when(voucherOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        handler.handle(new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L));

        verify(voucherMapper, never()).selectById(1L);
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
        verify(flashSaleVoucherMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void handleThrowsWhenMysqlStockUpdateFails() {
        when(voucherOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(voucherMapper.selectById(1L)).thenReturn(voucher());
        when(voucherOrderMapper.insert(any(VoucherOrder.class))).thenReturn(1);
        when(flashSaleVoucherMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> handler.handle(new FlashSaleOrderCommand("LSO202605200000000001", 1L, 10L)))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH));
    }

    private static Voucher voucher() {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setMerchantId(100L);
        voucher.setPayAmountCent(990L);
        voucher.setStatus(1);
        return voucher;
    }
}
