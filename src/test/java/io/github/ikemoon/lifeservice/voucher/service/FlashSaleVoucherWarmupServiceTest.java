package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheClient;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FlashSaleVoucherWarmupServiceTest {

    @Mock
    private FlashSaleVoucherMapper flashSaleVoucherMapper;

    @Mock
    private VoucherOrderMapper voucherOrderMapper;

    @Mock
    private CacheClient cacheClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private FlashSaleVoucherWarmupService service;

    @BeforeEach
    void setUp() {
        service = new FlashSaleVoucherWarmupService(
                flashSaleVoucherMapper,
                voucherOrderMapper,
                cacheClient,
                redisTemplate);
    }

    @Test
    void warmUpWritesVoucherMetadataStockAndRebuiltUsersSet() {
        FlashSaleVoucher voucher = flashSaleVoucher(1001L, 12000, LocalDateTime.now().plusHours(2));
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(voucher);
        when(voucherOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                voucherOrder(2001L),
                voucherOrder(2002L)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        FlashSaleVoucherWarmupResult result = service.warmUp(1001L);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cacheClient).set(
                eq(CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + 1001L),
                same(voucher),
                ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isGreaterThan(Duration.ofMinutes(30));
        verify(valueOperations).set(CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + 1001L, "12000");
        ArgumentCaptor<String> tempUsersKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(setOperations).add(tempUsersKeyCaptor.capture(), eq(CacheConstants.FLASH_SALE_USERS_READY_MARKER));
        String tempUsersKey = tempUsersKeyCaptor.getValue();
        assertThat(tempUsersKey).startsWith(CacheConstants.FLASH_SALE_USERS_KEY_PREFIX + 1001L + ":warmup:");
        verify(setOperations).add(tempUsersKey, "2001", "2002");
        verify(redisTemplate).rename(tempUsersKey, CacheConstants.FLASH_SALE_USERS_KEY_PREFIX + 1001L);
        assertThat(result.stock()).isEqualTo(12000);
        assertThat(result.warmedUserCount()).isEqualTo(2);
        assertThat(result.voucherCacheKey()).isEqualTo(CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + 1001L);
        assertThat(result.stockKey()).isEqualTo(CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + 1001L);
        assertThat(result.usersKey()).isEqualTo(CacheConstants.FLASH_SALE_USERS_KEY_PREFIX + 1001L);
    }

    @Test
    void warmUpThrowsNotFoundWhenVoucherDoesNotExist() {
        when(flashSaleVoucherMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.warmUp(1001L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(cacheClient, never()).set(any(), any(), any());
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForSet();
    }

    private static FlashSaleVoucher flashSaleVoucher(Long voucherId, Integer stock, LocalDateTime endTime) {
        FlashSaleVoucher voucher = new FlashSaleVoucher();
        voucher.setVoucherId(voucherId);
        voucher.setStock(stock);
        voucher.setStartTime(LocalDateTime.now().minusMinutes(10));
        voucher.setEndTime(endTime);
        voucher.setStatus(2);
        return voucher;
    }

    private static VoucherOrder voucherOrder(Long userId) {
        VoucherOrder order = new VoucherOrder();
        order.setUserId(userId);
        return order;
    }
}
