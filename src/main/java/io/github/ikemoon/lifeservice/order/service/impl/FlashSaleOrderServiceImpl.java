package io.github.ikemoon.lifeservice.order.service.impl;

import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheClient;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderCommand;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderMessagePublisher;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class FlashSaleOrderServiceImpl implements FlashSaleOrderService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleOrderServiceImpl.class);
    private static final DefaultRedisScript<Long> FLASH_SALE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> FLASH_SALE_ROLLBACK_SCRIPT = new DefaultRedisScript<>();

    static {
        FLASH_SALE_SCRIPT.setLocation(new ClassPathResource("scripts/flash_sale.lua"));
        FLASH_SALE_SCRIPT.setResultType(Long.class);
        FLASH_SALE_ROLLBACK_SCRIPT.setLocation(new ClassPathResource("scripts/flash_sale_rollback.lua"));
        FLASH_SALE_ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final CacheClient cacheClient;
    private final OrderNoGenerator orderNoGenerator;
    private final FlashSaleOrderMessagePublisher orderMessagePublisher;

    public FlashSaleOrderServiceImpl(
            StringRedisTemplate redisTemplate,
            CacheClient cacheClient,
            OrderNoGenerator orderNoGenerator,
            FlashSaleOrderMessagePublisher orderMessagePublisher) {
        this.redisTemplate = redisTemplate;
        this.cacheClient = cacheClient;
        this.orderNoGenerator = orderNoGenerator;
        this.orderMessagePublisher = orderMessagePublisher;
    }

    @Override
    public FlashSaleOrderResult seckill(Long voucherId, Long userId) {
        if (userId == null) {
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Missing user id");
        }

        FlashSaleVoucher flashSaleVoucher = cacheClient.get(
                CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + voucherId,
                FlashSaleVoucher.class);
        if (flashSaleVoucher == null) {
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_NOT_READY, "Flash sale is not ready");
        }
        FlashSaleOrderResult saleTimeResult = validateSaleTime(flashSaleVoucher);
        if (!saleTimeResult.success()) {
            return saleTimeResult;
        }

        Long result = redisTemplate.execute(
                FLASH_SALE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Flash sale qualification check failed");
        }
        if (Long.valueOf(1L).equals(result)) {
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH, "Stock not enough");
        }
        if (Long.valueOf(2L).equals(result)) {
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_DUPLICATE_ORDER, "Duplicate flash sale order");
        }
        if (Long.valueOf(3L).equals(result)) {
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_NOT_READY, "Flash sale is not ready");
        }
        if (!Long.valueOf(0L).equals(result)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Unexpected flash sale qualification result");
        }

        try {
            String orderNo = orderNoGenerator.nextOrderNo("LSO");
            orderMessagePublisher.publish(new FlashSaleOrderCommand(orderNo, voucherId, userId));
            return FlashSaleOrderResult.success(orderNo);
        } catch (RuntimeException e) {
            rollbackFlashSaleQualification(voucherId, userId, e);
            log.error("Flash sale order enqueue failed, voucherId={}, userId={}", voucherId, userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Flash sale order enqueue failed", e);
        }
    }

    private FlashSaleOrderResult validateSaleTime(FlashSaleVoucher voucher) {
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Flash sale has not started");
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Flash sale has ended");
        }
        return FlashSaleOrderResult.success(null);
    }

    private void rollbackFlashSaleQualification(Long voucherId, Long userId, RuntimeException cause) {
        try {
            Long result = redisTemplate.execute(
                    FLASH_SALE_ROLLBACK_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString());
            if (!Long.valueOf(1L).equals(result)) {
                log.warn("Flash sale reservation rollback skipped, voucherId={}, userId={}, result={}",
                        voucherId, userId, result);
            }
        } catch (RuntimeException rollbackException) {
            cause.addSuppressed(rollbackException);
            log.error("Flash sale reservation rollback failed, voucherId={}, userId={}", voucherId, userId,
                    rollbackException);
        }
    }
}
