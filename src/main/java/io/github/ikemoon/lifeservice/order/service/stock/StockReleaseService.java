package io.github.ikemoon.lifeservice.order.service.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.order.service.close.OrderCloseProperties;
import io.github.ikemoon.lifeservice.order.entity.StockReleaseTask;
import io.github.ikemoon.lifeservice.order.enums.StockReleaseTaskStatus;
import io.github.ikemoon.lifeservice.order.mapper.StockReleaseTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockReleaseService {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseService.class);
    private static final DefaultRedisScript<Long> RELEASE_REDIS_STOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_REDIS_STOCK_SCRIPT.setScriptText("""
                if (redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1) then
                    return 0
                end
                if (redis.call('EXISTS', KEYS[1]) == 0) then
                    return -1
                end
                if (redis.call('SADD', KEYS[2], ARGV[1]) == 1) then
                    redis.call('INCRBY', KEYS[1], 1)
                    return 1
                end
                return 0
                """);
        RELEASE_REDIS_STOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final StockReleaseTxService stockReleaseTxService;
    private final StockReleaseTaskMapper stockReleaseTaskMapper;
    private final OrderCloseProperties properties;

    public StockReleaseService(
            StringRedisTemplate redisTemplate,
            StockReleaseTxService stockReleaseTxService,
            StockReleaseTaskMapper stockReleaseTaskMapper,
            OrderCloseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.stockReleaseTxService = stockReleaseTxService;
        this.stockReleaseTaskMapper = stockReleaseTaskMapper;
        this.properties = properties;
    }

    public boolean release(StockReleaseTask task) {
        try {
            releaseRedisStock(task);
            stockReleaseTxService.releaseMysqlStockAndMarkSuccess(task);
            return true;
        } catch (RuntimeException e) {
            RetryDecision decision = markRetry(task, e);
            if (decision.failed()) {
                log.error("Stock release retry exhausted, orderId={}, voucherId={}, retryCount={}",
                        task.getOrderId(), task.getVoucherId(), decision.retryCount(), e);
            } else {
                log.warn("Stock release failed, orderId={}, voucherId={}, retryCount={}",
                        task.getOrderId(), task.getVoucherId(), decision.retryCount(), e);
            }
            return false;
        }
    }

    public int retryPendingTasks() {
        List<StockReleaseTask> tasks = stockReleaseTaskMapper.selectList(new QueryWrapper<StockReleaseTask>()
                .eq("status", StockReleaseTaskStatus.PENDING.code())
                .le("next_retry_at", LocalDateTime.now())
                .orderByAsc("next_retry_at")
                .last("limit " + normalizeBatchSize(properties.getStockReleaseRetryBatchSize())));
        int released = 0;
        for (StockReleaseTask task : tasks) {
            if (release(task)) {
                released++;
            }
        }
        return released;
    }

    private void releaseRedisStock(StockReleaseTask task) {
        Long result = redisTemplate.execute(
                RELEASE_REDIS_STOCK_SCRIPT,
                List.of(stockKey(task.getVoucherId()), releasedOrderKey(task.getVoucherId())),
                task.getOrderId().toString());
        if (result == null || result < 0) {
            throw new IllegalStateException("Redis flash sale stock key is missing");
        }
    }

    private RetryDecision markRetry(StockReleaseTask task, RuntimeException error) {
        int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        int nextStatus = nextRetryCount >= properties.getStockReleaseMaxRetryCount()
                ? StockReleaseTaskStatus.FAILED.code()
                : StockReleaseTaskStatus.PENDING.code();
        LocalDateTime now = LocalDateTime.now();
        stockReleaseTaskMapper.update(null, new UpdateWrapper<StockReleaseTask>()
                .set("status", nextStatus)
                .set("reason", abbreviate(error.getMessage()))
                .set("retry_count", nextRetryCount)
                .set("next_retry_at", now.plus(properties.getStockReleaseRetryDelay()))
                .set("updated_at", now)
                .eq("id", task.getId())
                .eq("status", StockReleaseTaskStatus.PENDING.code()));
        return new RetryDecision(nextRetryCount, nextStatus == StockReleaseTaskStatus.FAILED.code());
    }

    private static String stockKey(Long voucherId) {
        return CacheConstants.FLASH_SALE_STOCK_KEY_PREFIX + voucherId;
    }

    private static String releasedOrderKey(Long voucherId) {
        return CacheConstants.FLASH_SALE_RELEASED_ORDER_KEY_PREFIX + voucherId;
    }

    private static int normalizeBatchSize(int batchSize) {
        return Math.max(1, Math.min(batchSize, 500));
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private record RetryDecision(int retryCount, boolean failed) {
    }
}
