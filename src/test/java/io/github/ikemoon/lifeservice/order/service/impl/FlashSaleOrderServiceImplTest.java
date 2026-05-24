package io.github.ikemoon.lifeservice.order.service.impl;

import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.infrastructure.metrics.FlashSaleMetrics;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderCommand;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderMessagePublisher;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

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
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private FlashSaleOrderMessagePublisher orderMessagePublisher;

    private SimpleMeterRegistry meterRegistry;

    private FlashSaleOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new FlashSaleOrderServiceImpl(
                redisTemplate,
                orderNoGenerator,
                orderMessagePublisher,
                new FlashSaleMetrics(meterRegistry));
    }

    @Test
    void seckillReturnsBadRequestWhenUserIdMissing() {
        FlashSaleOrderResult result = service.seckill(1L, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(counter("life.flash.sale.request")).isEqualTo(1);
    }

    @Test
    void seckillReturnsNotReadyWhenRedisHotDataIsMissing() {
        whenRedisQualificationReturns(3L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_NOT_READY);
        assertThat(counter("life.flash.sale.not.ready")).isEqualTo(1);
    }

    @Test
    void seckillReturnsBadRequestWhenSaleHasNotStarted() {
        whenRedisQualificationReturns(4L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void seckillReturnsBadRequestWhenSaleHasEnded() {
        whenRedisQualificationReturns(5L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void seckillReturnsStockNotEnoughWhenRedisReportsInsufficientStock() {
        whenRedisQualificationReturns(1L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH);
        assertThat(counter("life.flash.sale.stock.not.enough")).isEqualTo(1);
    }

    @Test
    void seckillReturnsDuplicateOrderWhenRedisReportsDuplicateOrder() {
        whenRedisQualificationReturns(2L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_DUPLICATE_ORDER);
        assertThat(counter("life.flash.sale.duplicate")).isEqualTo(1);
    }

    @Test
    void seckillReturnsNotReadyWhenRedisReportsNotReady() {
        whenRedisQualificationReturns(3L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_NOT_READY);
        assertThat(counter("life.flash.sale.not.ready")).isEqualTo(1);
        verify(orderNoGenerator, never()).nextOrderNo(anyString());
    }

    @Test
    void seckillPublishesOrderCommandWhenQualified() {
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
        assertThat(counter("life.flash.sale.success")).isEqualTo(1);
    }

    @Test
    void seckillRollsBackRedisReservationWhenOrderNoGenerationFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L)
                .thenReturn(1L);
        when(orderNoGenerator.nextOrderNo("LSO")).thenThrow(new IllegalStateException("sequence unavailable"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), anyString(), anyString());
        verify(orderMessagePublisher, never()).publish(any(FlashSaleOrderCommand.class));
        assertThat(counter("life.flash.sale.mq.publish.failure")).isZero();
    }

    @Test
    void seckillRollsBackRedisReservationWhenPublishFails() {
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
        assertThat(counter("life.flash.sale.mq.publish.failure")).isEqualTo(1);
    }

    @Test
    void seckillRecordsRollbackFailureWhenPublishRollbackFails() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L)
                .thenThrow(new IllegalStateException("redis down"));
        when(orderNoGenerator.nextOrderNo("LSO")).thenReturn("LSO202605200000000001");
        doThrow(new IllegalStateException("rocketmq unavailable"))
                .when(orderMessagePublisher)
                .publish(any(FlashSaleOrderCommand.class));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.seckill(1L, 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        assertThat(counter("life.flash.sale.mq.publish.failure")).isEqualTo(1);
        assertThat(counter("life.flash.sale.redis.rollback.failure")).isEqualTo(1);
    }

    @Test
    void seckillUsesRedisHotKeysForQualification() {
        whenRedisQualificationReturns(1L);

        FlashSaleOrderResult result = service.seckill(1L, 10L);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString(), anyString());
    }

    private void whenRedisQualificationReturns(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(result);
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }
}
