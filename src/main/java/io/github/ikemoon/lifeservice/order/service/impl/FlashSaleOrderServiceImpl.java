package io.github.ikemoon.lifeservice.order.service.impl;

import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.infrastructure.metrics.FlashSaleMetrics;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderCommand;
import io.github.ikemoon.lifeservice.order.messaging.FlashSaleOrderMessagePublisher;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final OrderNoGenerator orderNoGenerator;
    private final FlashSaleOrderMessagePublisher orderMessagePublisher;
    private final FlashSaleMetrics flashSaleMetrics;

    public FlashSaleOrderServiceImpl(
            StringRedisTemplate redisTemplate,
            OrderNoGenerator orderNoGenerator,
            FlashSaleOrderMessagePublisher orderMessagePublisher,
            FlashSaleMetrics flashSaleMetrics) {
        this.redisTemplate = redisTemplate;
        this.orderNoGenerator = orderNoGenerator;
        this.orderMessagePublisher = orderMessagePublisher;
        this.flashSaleMetrics = flashSaleMetrics;
    }

    @Override
    public FlashSaleOrderResult seckill(Long voucherId, Long userId) {
        flashSaleMetrics.recordRequest();
        if (userId == null) {
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Missing user id");
        }

        String metadataKey = CacheConstants.FLASH_SALE_VOUCHER_KEY_PREFIX + voucherId;
        String stockKey = CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + voucherId;
        String usersKey = CacheConstants.FLASH_SALE_USERS_KEY_PREFIX + voucherId;

        Long result = redisTemplate.execute(
                FLASH_SALE_SCRIPT,
                List.of(metadataKey, stockKey, usersKey),
                userId.toString(),
                String.valueOf(System.currentTimeMillis()));
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Flash sale qualification check failed");
        }
        if (Long.valueOf(1L).equals(result)) {
            flashSaleMetrics.recordStockNotEnough();
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH, "Stock not enough");
        }
        if (Long.valueOf(2L).equals(result)) {
            flashSaleMetrics.recordDuplicate();
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_DUPLICATE_ORDER, "Duplicate flash sale order");
        }
        if (Long.valueOf(3L).equals(result)) {
            flashSaleMetrics.recordNotReady();
            return FlashSaleOrderResult.fail(ErrorCode.FLASH_SALE_NOT_READY, "Flash sale is not ready");
        }
        if (Long.valueOf(4L).equals(result)) {
            flashSaleMetrics.recordNotReady();
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Flash sale has not started");
        }
        if (Long.valueOf(5L).equals(result)) {
            flashSaleMetrics.recordNotReady();
            return FlashSaleOrderResult.fail(ErrorCode.BAD_REQUEST, "Flash sale has ended");
        }
        if (!Long.valueOf(0L).equals(result)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Unexpected flash sale qualification result");
        }

        String orderNo;
        try {
            orderNo = orderNoGenerator.nextOrderNo("LSO");
        } catch (RuntimeException e) {
            rollbackFlashSaleQualification(voucherId, userId, e);
            log.error("Flash sale order no generation failed, voucherId={}, userId={}", voucherId, userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Flash sale order enqueue failed", e);
        }

        try {
            orderMessagePublisher.publish(new FlashSaleOrderCommand(orderNo, voucherId, userId));
            flashSaleMetrics.recordSuccess();
            return FlashSaleOrderResult.success(orderNo);
        } catch (RuntimeException e) {
            flashSaleMetrics.recordMqPublishFailure();
            rollbackFlashSaleQualification(voucherId, userId, e);
            log.error("Flash sale order enqueue failed, voucherId={}, userId={}", voucherId, userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Flash sale order enqueue failed", e);
        }
    }

    private void rollbackFlashSaleQualification(Long voucherId, Long userId, RuntimeException cause) {
        try {
            Long result = redisTemplate.execute(
                    FLASH_SALE_ROLLBACK_SCRIPT,
                    List.of(),
                    voucherId.toString(),
                    userId.toString());
            if (!Long.valueOf(1L).equals(result)) {
                log.warn("Flash sale reservation rollback skipped, voucherId={}, userId={}, result={}",
                        voucherId, userId, result);
            }
        } catch (RuntimeException rollbackException) {
            flashSaleMetrics.recordRedisRollbackFailure();
            cause.addSuppressed(rollbackException);
            log.error("Flash sale reservation rollback failed, voucherId={}, userId={}", voucherId, userId,
                    rollbackException);
        }
    }
}
