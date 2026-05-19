package io.github.ikemoon.lifeservice.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.id.OrderNoGenerator;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class FlashSaleOrderServiceImpl implements FlashSaleOrderService {

    private static final int ORDER_STATUS_PENDING_PAYMENT = 1;
    private static final DefaultRedisScript<Long> FLASH_SALE_SCRIPT = new DefaultRedisScript<>();

    static {
        FLASH_SALE_SCRIPT.setLocation(new ClassPathResource("scripts/flash_sale.lua"));
        FLASH_SALE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final OrderNoGenerator orderNoGenerator;
    private final VoucherMapper voucherMapper;
    private final FlashSaleVoucherMapper flashSaleVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;

    public FlashSaleOrderServiceImpl(
            StringRedisTemplate redisTemplate,
            OrderNoGenerator orderNoGenerator,
            VoucherMapper voucherMapper,
            FlashSaleVoucherMapper flashSaleVoucherMapper,
            VoucherOrderMapper voucherOrderMapper) {
        this.redisTemplate = redisTemplate;
        this.orderNoGenerator = orderNoGenerator;
        this.voucherMapper = voucherMapper;
        this.flashSaleVoucherMapper = flashSaleVoucherMapper;
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String seckill(Long voucherId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少用户标识");
        }

        FlashSaleVoucher flashSaleVoucher = flashSaleVoucherMapper.selectOne(
                new LambdaQueryWrapper<FlashSaleVoucher>().eq(FlashSaleVoucher::getVoucherId, voucherId));
        if (flashSaleVoucher == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "秒杀券不存在");
        }
        validateSaleTime(flashSaleVoucher);

        Long result = redisTemplate.execute(
                FLASH_SALE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "秒杀资格校验失败");
        }
        if (result == 1L) {
            throw new BusinessException(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH, "库存不足");
        }
        if (result == 2L) {
            throw new BusinessException(ErrorCode.FLASH_SALE_DUPLICATE_ORDER, "不能重复下单");
        }

        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在");
        }

        boolean stockUpdated = flashSaleVoucherMapper.update(null, new LambdaUpdateWrapper<FlashSaleVoucher>()
                .setSql("stock = stock - 1")
                .eq(FlashSaleVoucher::getVoucherId, voucherId)
                .gt(FlashSaleVoucher::getStock, 0)) == 1;
        if (!stockUpdated) {
            throw new BusinessException(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH, "库存不足");
        }

        String orderNo = orderNoGenerator.nextOrderNo("LSO");
        VoucherOrder order = new VoucherOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setMerchantId(voucher.getMerchantId());
        order.setPayAmountCent(voucher.getPayAmountCent());
        order.setStatus(ORDER_STATUS_PENDING_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        voucherOrderMapper.insert(order);
        return orderNo;
    }

    private void validateSaleTime(FlashSaleVoucher voucher) {
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "秒杀尚未开始");
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "秒杀已结束");
        }
    }
}
