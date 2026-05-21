package io.github.ikemoon.lifeservice.order.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class VoucherOrderPaymentService {

    private final VoucherOrderMapper voucherOrderMapper;

    public VoucherOrderPaymentService(VoucherOrderMapper voucherOrderMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public VoucherOrderPaymentResult pay(String orderNo, Long userId) {
        if (!StringUtils.hasText(orderNo)) {
            return VoucherOrderPaymentResult.fail(ErrorCode.BAD_REQUEST, "Missing order no");
        }
        if (userId == null) {
            return VoucherOrderPaymentResult.fail(ErrorCode.BAD_REQUEST, "Missing user id");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean paid = voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", OrderStatus.PAID.code())
                .set("paid_at", now)
                .set("updated_at", now)
                .eq("order_no", orderNo)
                .eq("user_id", userId)
                .eq("status", OrderStatus.PENDING_PAYMENT.code())) == 1;
        if (paid) {
            return VoucherOrderPaymentResult.paid(orderNo, OrderStatus.PAID.code());
        }

        VoucherOrder order = voucherOrderMapper.selectOne(new QueryWrapper<VoucherOrder>()
                .eq("order_no", orderNo)
                .eq("user_id", userId));
        if (order == null) {
            return VoucherOrderPaymentResult.fail(ErrorCode.NOT_FOUND, "Voucher order not found");
        }
        if (Integer.valueOf(OrderStatus.PAID.code()).equals(order.getStatus())) {
            return VoucherOrderPaymentResult.alreadyPaid(orderNo, OrderStatus.PAID.code());
        }
        if (Integer.valueOf(OrderStatus.CLOSED.code()).equals(order.getStatus())) {
            return VoucherOrderPaymentResult.fail(ErrorCode.ORDER_CLOSED, "Voucher order already closed");
        }
        return VoucherOrderPaymentResult.fail(ErrorCode.BAD_REQUEST, "Voucher order is not payable");
    }
}
