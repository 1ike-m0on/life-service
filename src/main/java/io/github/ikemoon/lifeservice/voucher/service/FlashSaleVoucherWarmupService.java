package io.github.ikemoon.lifeservice.voucher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class FlashSaleVoucherWarmupService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleVoucherWarmupService.class);
    private static final Duration MIN_METADATA_TTL = Duration.ofMinutes(30);
    private static final Duration METADATA_GRACE_TTL = Duration.ofMinutes(30);

    private final FlashSaleVoucherMapper flashSaleVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final StringRedisTemplate redisTemplate;

    public FlashSaleVoucherWarmupService(
            FlashSaleVoucherMapper flashSaleVoucherMapper,
            VoucherOrderMapper voucherOrderMapper,
            StringRedisTemplate redisTemplate) {
        this.flashSaleVoucherMapper = flashSaleVoucherMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.redisTemplate = redisTemplate;
    }

    public FlashSaleVoucherWarmupResult warmUp(Long voucherId) {
        FlashSaleVoucher voucher = flashSaleVoucherMapper.selectOne(new LambdaQueryWrapper<FlashSaleVoucher>()
                .eq(FlashSaleVoucher::getVoucherId, voucherId));
        if (voucher == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Flash sale voucher not found");
        }

        String voucherCacheKey = CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + voucherId;
        String stockKey = CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + voucherId;
        String usersKey = CacheConstants.FLASH_SALE_USERS_KEY_PREFIX + voucherId;
        Duration metadataTtl = metadataTtl(voucher.getEndTime());
        List<String> purchasedUserIds = purchasedUserIds(voucherId);

        rebuildVoucherMetadata(voucherCacheKey, voucher, metadataTtl);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
        rebuildUsersKey(usersKey, purchasedUserIds);

        log.info("Flash sale voucher warmup finished, voucherId={}, stock={}, warmedUserCount={}, metadataTtlSeconds={}",
                voucherId, voucher.getStock(), purchasedUserIds.size(), metadataTtl.toSeconds());
        return new FlashSaleVoucherWarmupResult(
                voucherId,
                voucher.getStock(),
                purchasedUserIds.size(),
                metadataTtl.toSeconds(),
                voucherCacheKey,
                stockKey,
                usersKey);
    }

    private static Duration metadataTtl(LocalDateTime endTime) {
        if (endTime == null) {
            return MIN_METADATA_TTL;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), endTime).plus(METADATA_GRACE_TTL);
        if (ttl.compareTo(MIN_METADATA_TTL) < 0) {
            return MIN_METADATA_TTL;
        }
        return ttl;
    }

    private void rebuildVoucherMetadata(String voucherCacheKey, FlashSaleVoucher voucher, Duration metadataTtl) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("voucherId", String.valueOf(voucher.getVoucherId()));
        metadata.put("status", String.valueOf(voucher.getStatus()));
        metadata.put("startTime", String.valueOf(toEpochMillis(voucher.getStartTime())));
        metadata.put("endTime", String.valueOf(toEpochMillis(voucher.getEndTime())));

        redisTemplate.delete(voucherCacheKey);
        redisTemplate.opsForHash().putAll(voucherCacheKey, metadata);
        redisTemplate.expire(voucherCacheKey, metadataTtl);
    }

    private static long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<String> purchasedUserIds(Long voucherId) {
        return voucherOrderMapper.selectList(new QueryWrapper<VoucherOrder>()
                        .select("user_id")
                        .eq("voucher_id", voucherId))
                .stream()
                .map(VoucherOrder::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .toList();
    }

    private void rebuildUsersKey(String usersKey, List<String> purchasedUserIds) {
        String tempUsersKey = usersKey + ":warmup:" + UUID.randomUUID();
        redisTemplate.delete(tempUsersKey);
        redisTemplate.opsForSet().add(tempUsersKey, CacheConstants.FLASH_SALE_USERS_READY_MARKER);
        if (!purchasedUserIds.isEmpty()) {
            redisTemplate.opsForSet().add(tempUsersKey, purchasedUserIds.toArray(String[]::new));
        }
        redisTemplate.rename(tempUsersKey, usersKey);
    }
}
