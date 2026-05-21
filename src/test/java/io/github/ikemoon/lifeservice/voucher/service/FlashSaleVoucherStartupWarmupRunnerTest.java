package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FlashSaleVoucherStartupWarmupRunnerTest {

    @Mock
    private FlashSaleVoucherMapper flashSaleVoucherMapper;

    @Mock
    private FlashSaleVoucherWarmupService warmupService;

    private FlashSaleVoucherWarmupProperties properties;
    private FlashSaleVoucherStartupWarmupRunner runner;

    @BeforeEach
    void setUp() {
        properties = new FlashSaleVoucherWarmupProperties();
        runner = new FlashSaleVoucherStartupWarmupRunner(properties, flashSaleVoucherMapper, warmupService);
    }

    @Test
    void runSkipsWarmupWhenDisabled() {
        properties.setEnabled(false);

        runner.run(null);

        verify(flashSaleVoucherMapper, never()).selectList(any());
        verify(warmupService, never()).warmUp(any());
    }

    @Test
    void runWarmsEligibleVouchersAndDeduplicatesIds() {
        when(flashSaleVoucherMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                flashSaleVoucher(1001L),
                flashSaleVoucher(1002L),
                flashSaleVoucher(1001L)));

        runner.run(null);

        verify(warmupService).warmUp(1001L);
        verify(warmupService).warmUp(1002L);
    }

    @Test
    void runContinuesWhenOneWarmupFailsByDefault() {
        when(flashSaleVoucherMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                flashSaleVoucher(1001L),
                flashSaleVoucher(1002L)));
        when(warmupService.warmUp(1001L)).thenThrow(new IllegalStateException("redis unavailable"));

        runner.run(null);

        verify(warmupService).warmUp(1001L);
        verify(warmupService).warmUp(1002L);
    }

    @Test
    void runThrowsWhenFailFastIsEnabled() {
        properties.setFailFast(true);
        when(flashSaleVoucherMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                flashSaleVoucher(1001L),
                flashSaleVoucher(1002L)));
        when(warmupService.warmUp(1001L)).thenThrow(new IllegalStateException("redis unavailable"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> runner.run(null));

        verify(warmupService).warmUp(1001L);
        verify(warmupService, never()).warmUp(1002L);
    }

    private static FlashSaleVoucher flashSaleVoucher(Long voucherId) {
        FlashSaleVoucher voucher = new FlashSaleVoucher();
        voucher.setVoucherId(voucherId);
        return voucher;
    }
}
