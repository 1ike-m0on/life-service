package io.github.ikemoon.lifeservice.order.service.stock;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.infrastructure.metrics.OrderMetrics;
import io.github.ikemoon.lifeservice.order.service.close.OrderCloseProperties;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class StockReleaseServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StockReleaseTxService stockReleaseTxService;

    @Mock
    private StockReleaseTaskMapper stockReleaseTaskMapper;

    private SimpleMeterRegistry meterRegistry;

    private StockReleaseService service;

    @BeforeEach
    void setUp() {
        OrderCloseProperties properties = new OrderCloseProperties();
        properties.setStockReleaseRetryDelay(Duration.ofMinutes(1));
        properties.setStockReleaseMaxRetryCount(3);
        meterRegistry = new SimpleMeterRegistry();
        service = new StockReleaseService(
                redisTemplate,
                stockReleaseTxService,
                stockReleaseTaskMapper,
                properties,
                new OrderMetrics(meterRegistry));
    }

    @Test
    void releaseReturnsTrueWhenRedisAndMysqlReleaseSucceed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        boolean released = service.release(task(0));

        assertThat(released).isTrue();
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), orderIdCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactly(
                CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + 1L,
                CacheConstants.FLASH_SALE_RELEASED_ORDER_KEY_PREFIX + 1L);
        assertThat(orderIdCaptor.getValue()).isEqualTo("100");
        verify(stockReleaseTxService).releaseMysqlStockAndMarkSuccess(any(StockReleaseTask.class));
        verify(stockReleaseTaskMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void releaseReturnsTrueWhenRedisStockWasAlreadyReleased() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(0L);

        boolean released = service.release(task(0));

        assertThat(released).isTrue();
        verify(stockReleaseTxService).releaseMysqlStockAndMarkSuccess(any(StockReleaseTask.class));
    }

    @Test
    void releaseMarksRetryWhenRedisStockKeyIsMissing() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(-1L);
        when(stockReleaseTaskMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        boolean released = service.release(task(0));

        assertThat(released).isFalse();
        verify(stockReleaseTxService, never()).releaseMysqlStockAndMarkSuccess(any(StockReleaseTask.class));
        ArgumentCaptor<UpdateWrapper<StockReleaseTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(stockReleaseTaskMapper).update(any(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet()).contains("status", "reason", "retry_count", "next_retry_at");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
                .contains(
                        StockReleaseTaskStatus.PENDING.code(),
                        "Redis flash sale stock key is missing",
                        1);
        assertThat(counter("life.stock.release.failure")).isEqualTo(1);
        assertThat(counter("life.stock.release.retry")).isEqualTo(1);
    }

    @Test
    void releaseMarksFailedWhenMaxRetryCountReached() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(-1L);
        when(stockReleaseTaskMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        boolean released = service.release(task(2));

        assertThat(released).isFalse();
        ArgumentCaptor<UpdateWrapper<StockReleaseTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(stockReleaseTaskMapper).update(any(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet()).contains("status", "reason", "retry_count", "next_retry_at");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
                .contains(
                        StockReleaseTaskStatus.FAILED.code(),
                        "Redis flash sale stock key is missing",
                        3);
        assertThat(counter("life.stock.release.failure")).isEqualTo(1);
        assertThat(counter("life.stock.release.retry")).isZero();
    }

    private static StockReleaseTask task(int retryCount) {
        StockReleaseTask task = new StockReleaseTask();
        task.setId(1L);
        task.setOrderId(100L);
        task.setOrderNo("LSO202605200000000001");
        task.setVoucherId(1L);
        task.setUserId(10L);
        task.setStatus(StockReleaseTaskStatus.PENDING.code());
        task.setRetryCount(retryCount);
        return task;
    }

    private double counter(String name) {
        return meterRegistry.counter(name).count();
    }
}
