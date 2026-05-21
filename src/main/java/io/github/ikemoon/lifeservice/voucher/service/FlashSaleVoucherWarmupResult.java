package io.github.ikemoon.lifeservice.voucher.service;

public record FlashSaleVoucherWarmupResult(
        Long voucherId,
        Integer stock,
        long warmedUserCount,
        long metadataTtlSeconds,
        String voucherCacheKey,
        String stockKey,
        String usersKey) {
}
