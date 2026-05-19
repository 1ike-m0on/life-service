package io.github.ikemoon.lifeservice.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashSaleOrderServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private FlashSaleVoucherMapper flashSaleVoucherMapper;

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    private FlashSaleOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlashSaleOrderServiceImpl(
                redisTemplate,
                orderNoGenerator,
                voucherMapper,
                flashSaleVoucherMapper,
                voucherOrderMapper);
    }

    @Test
    void seckillThrowsWhenUserIdMissing() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, null))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void seckillThrowsWhenFlashSaleVoucherMissing() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString());
    }

    @Test
    void seckillThrowsWhenSaleHasNotStarted() {
        FlashSaleVoucher flashSaleVoucher = activeFlashSaleVoucher();
        flashSaleVoucher.setStartTime(LocalDateTime.now().plusMinutes(1));
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(flashSaleVoucher);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void seckillThrowsWhenRedisReportsInsufficientStock() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFlashSaleVoucher());
        whenRedisQualificationReturns(1L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH));
    }

    @Test
    void seckillThrowsWhenRedisReportsDuplicateOrder() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFlashSaleVoucher());
        whenRedisQualificationReturns(2L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FLASH_SALE_DUPLICATE_ORDER));
    }

    @Test
    void seckillThrowsWhenDatabaseStockUpdateFails() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFlashSaleVoucher());
        whenRedisQualificationReturns(0L);
        when(voucherMapper.selectById(1L)).thenReturn(voucher());
        when(flashSaleVoucherMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH));
    }

    @Test
    void seckillCreatesPendingPaymentOrderWhenQualified() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(activeFlashSaleVoucher());
        whenRedisQualificationReturns(0L);
        when(voucherMapper.selectById(1L)).thenReturn(voucher());
        when(flashSaleVoucherMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(orderNoGenerator.nextOrderNo("LSO")).thenReturn("LSO202605200000000001");
        when(voucherOrderMapper.insert(any(VoucherOrder.class))).thenReturn(1);

        String orderNo = service.seckill(1L, 10L);

        assertThat(orderNo).isEqualTo("LSO202605200000000001");
        ArgumentCaptor<VoucherOrder> orderCaptor = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(voucherOrderMapper).insert(orderCaptor.capture());
        VoucherOrder order = orderCaptor.getValue();
        assertThat(order.getOrderNo()).isEqualTo("LSO202605200000000001");
        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getVoucherId()).isEqualTo(1L);
        assertThat(order.getMerchantId()).isEqualTo(100L);
        assertThat(order.getPayAmountCent()).isEqualTo(990L);
        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    private void whenRedisQualificationReturns(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(result);
    }

    private static FlashSaleVoucher activeFlashSaleVoucher() {
        FlashSaleVoucher voucher = new FlashSaleVoucher();
        voucher.setVoucherId(1L);
        voucher.setStock(10);
        voucher.setStartTime(LocalDateTime.now().minusMinutes(1));
        voucher.setEndTime(LocalDateTime.now().plusMinutes(10));
        voucher.setStatus(1);
        return voucher;
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
