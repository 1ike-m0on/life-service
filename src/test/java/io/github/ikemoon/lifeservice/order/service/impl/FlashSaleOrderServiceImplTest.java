package io.github.ikemoon.lifeservice.order.service.impl;

import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheClient;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderCommand;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderMessagePublisher;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class FlashSaleOrderServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CacheClient cacheClient;

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private FlashSaleOrderMessagePublisher orderMessagePublisher;

    private FlashSaleOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlashSaleOrderServiceImpl(
                redisTemplate,
                cacheClient,
                orderNoGenerator,
                orderMessagePublisher);
    }

    @Test
    void seckillReturnsBadRequestWhenUserIdMissing() {
        FlashSaleOrderResult result = service.seckill(1L, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void seckillReturnsNotReadyWhenFlashSaleVoucherCacheMissing() {
        whenFlashSaleVoucherCacheReturns(null);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_NOT_READY);
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString());
    }

    @Test
    void seckillReturnsBadRequestWhenSaleHasNotStarted() {
        FlashSaleVoucher flashSaleVoucher = activeFlashSaleVoucher();
        flashSaleVoucher.setStartTime(LocalDateTime.now().plusMinutes(1));
        whenFlashSaleVoucherCacheReturns(flashSaleVoucher);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void seckillReturnsBadRequestWhenSaleHasEnded() {
        FlashSaleVoucher flashSaleVoucher = activeFlashSaleVoucher();
        flashSaleVoucher.setEndTime(LocalDateTime.now().minusMinutes(1));
        whenFlashSaleVoucherCacheReturns(flashSaleVoucher);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void seckillReturnsStockNotEnoughWhenRedisReportsInsufficientStock() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        whenRedisQualificationReturns(1L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH);
    }

    @Test
    void seckillReturnsDuplicateOrderWhenRedisReportsDuplicateOrder() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        whenRedisQualificationReturns(2L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_DUPLICATE_ORDER);
    }

    @Test
    void seckillReturnsNotReadyWhenRedisHotDataIsMissing() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        whenRedisQualificationReturns(3L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_NOT_READY);
        verify(orderNoGenerator, never()).nextOrderNo(anyString());
    }

    @Test
    void seckillPublishesOrderCommandWhenQualified() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        whenRedisQualificationReturns(0L);
        when(orderNoGenerator.nextOrderNo("LSO")).thenReturn("LSO202605200000000001");

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isTrue();
        assertThat(result.orderNo()).isEqualTo("LSO202605200000000001");
        ArgumentCaptor<FlashSaleOrderCommand> commandCaptor = ArgumentCaptor.forClass(FlashSaleOrderCommand.class);
        verify(orderMessagePublisher).publish(commandCaptor.capture());
        FlashSaleOrderCommand command = commandCaptor.getValue();
        assertThat(command.orderNo()).isEqualTo("LSO202605200000000001");
        assertThat(command.voucherId()).isEqualTo(1L);
        assertThat(command.userId()).isEqualTo(10L);
    }

    @Test
    void seckillRollsBackRedisReservationWhenOrderNoGenerationFails() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L)
                .thenReturn(1L);
        when(orderNoGenerator.nextOrderNo("LSO")).thenThrow(new IllegalStateException("sequence unavailable"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), anyString(), anyString());
        verify(orderMessagePublisher, never()).publish(any(FlashSaleOrderCommand.class));
    }

    @Test
    void seckillRollsBackRedisReservationWhenPublishFails() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L)
                .thenReturn(1L);
        when(orderNoGenerator.nextOrderNo("LSO")).thenReturn("LSO202605200000000001");
        doThrow(new IllegalStateException("rocketmq unavailable"))
                .when(orderMessagePublisher)
                .publish(any(FlashSaleOrderCommand.class));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), anyString(), anyString());
    }

    @Test
    void seckillUsesFlashSaleVoucherHotCacheBeforeRedisQualification() {
        whenFlashSaleVoucherCacheReturns(activeFlashSaleVoucher());
        whenRedisQualificationReturns(1L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH);
        verify(cacheClient).get(CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + 1L, FlashSaleVoucher.class);
    }

    private void whenRedisQualificationReturns(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(result);
    }

    private void whenFlashSaleVoucherCacheReturns(FlashSaleVoucher voucher) {
        when(cacheClient.get(CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + 1L, FlashSaleVoucher.class))
                .thenReturn(voucher);
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
}
