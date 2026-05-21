package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
public class FlashSaleVoucherStartupWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleVoucherStartupWarmupRunner.class);
    private static final List<Integer> WARMUP_STATUSES = List.of(1, 2);

    private final FlashSaleVoucherWarmupProperties properties;
    private final FlashSaleVoucherMapper flashSaleVoucherMapper;
    private final FlashSaleVoucherWarmupService warmupService;

    public FlashSaleVoucherStartupWarmupRunner(
            FlashSaleVoucherWarmupProperties properties,
            FlashSaleVoucherMapper flashSaleVoucherMapper,
            FlashSaleVoucherWarmupService warmupService) {
        this.properties = properties;
        this.flashSaleVoucherMapper = flashSaleVoucherMapper;
        this.warmupService = warmupService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Startup flash sale voucher warmup is disabled");
            return;
        }

        List<Long> voucherIds = eligibleVoucherIds();
        if (voucherIds.isEmpty()) {
            log.info("No flash sale vouchers need startup warmup");
            return;
        }

        int successCount = 0;
        int failureCount = 0;
        for (Long voucherId : voucherIds) {
            try {
                warmupService.warmUp(voucherId);
                successCount++;
            } catch (RuntimeException e) {
                failureCount++;
                log.warn("Startup flash sale voucher warmup failed, voucherId={}", voucherId, e);
                if (properties.isFailFast()) {
                    throw e;
                }
            }
        }

        log.info("Startup flash sale voucher warmup finished, total={}, success={}, failure={}",
                voucherIds.size(), successCount, failureCount);
    }

    private List<Long> eligibleVoucherIds() {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper<FlashSaleVoucher> query = new QueryWrapper<FlashSaleVoucher>()
                .select("voucher_id")
                .in("status", WARMUP_STATUSES)
                .and(wrapper -> wrapper
                        .isNull("end_time")
                        .or()
                        .gt("end_time", now))
                .orderByAsc("start_time")
                .orderByAsc("id");

        if (properties.getMaxVouchers() > 0) {
            query.last("limit " + properties.getMaxVouchers());
        }

        return flashSaleVoucherMapper.selectList(query).stream()
                .map(FlashSaleVoucher::getVoucherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
