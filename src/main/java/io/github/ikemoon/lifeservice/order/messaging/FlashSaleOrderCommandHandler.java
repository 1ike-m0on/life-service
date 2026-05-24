package io.github.ikemoon.lifeservice.order.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.voucher.entity.FlashSaleVoucher;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.FlashSaleVoucherMapper;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FlashSaleOrderCommandHandler {

    private final VoucherMapper voucherMapper;
    private final FlashSaleVoucherMapper flashSaleVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;

    public FlashSaleOrderCommandHandler(
            VoucherMapper voucherMapper,
            FlashSaleVoucherMapper flashSaleVoucherMapper,
            VoucherOrderMapper voucherOrderMapper) {
        this.voucherMapper = voucherMapper;
        this.flashSaleVoucherMapper = flashSaleVoucherMapper;
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(FlashSaleOrderCommand command) {
        if (hasCreatedOrder(command)) {
            return;
        }

        Voucher voucher = voucherMapper.selectById(command.voucherId());
        if (voucher == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Voucher not found");
        }

        VoucherOrder order = buildPendingPaymentOrder(command, voucher);
        voucherOrderMapper.insert(order);

        boolean stockUpdated = flashSaleVoucherMapper.update(null, new LambdaUpdateWrapper<FlashSaleVoucher>()
                .setSql("stock = stock - 1")
                .eq(FlashSaleVoucher::getVoucherId, command.voucherId())
                .gt(FlashSaleVoucher::getStock, 0)) == 1;
        if (!stockUpdated) {
            throw new BusinessException(ErrorCode.FLASH_SALE_STOCK_NOT_ENOUGH, "Stock not enough");
        }
    }

    private boolean hasCreatedOrder(FlashSaleOrderCommand command) {
        Long count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, command.userId())
                .eq(VoucherOrder::getVoucherId, command.voucherId()));
        return count != null && count > 0;
    }

    private VoucherOrder buildPendingPaymentOrder(FlashSaleOrderCommand command, Voucher voucher) {
        LocalDateTime now = LocalDateTime.now();
        VoucherOrder order = new VoucherOrder();
        order.setOrderNo(command.orderNo());
        order.setUserId(command.userId());
        order.setVoucherId(command.voucherId());
        order.setMerchantId(voucher.getMerchantId());
        order.setPayAmountCent(voucher.getPayAmountCent());
        order.setStatus(OrderStatus.PENDING_PAYMENT.code());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }
}
