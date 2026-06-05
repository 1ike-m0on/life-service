package io.github.ikemoon.lifeservice.order.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.common.util.ImagePathParser;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderDetailResponse;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderSummaryResponse;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VoucherOrderQueryServiceImpl implements VoucherOrderQueryService {

    private final VoucherOrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final VoucherMapper voucherMapper;

    public VoucherOrderQueryServiceImpl(
            VoucherOrderMapper orderMapper,
            MerchantMapper merchantMapper,
            VoucherMapper voucherMapper) {
        this.orderMapper = orderMapper;
        this.merchantMapper = merchantMapper;
        this.voucherMapper = voucherMapper;
    }

    @Override
    public Page<VoucherOrderSummaryResponse> pageCurrentUserOrders(Long userId, Integer status, long pageNo, long pageSize) {
        validateStatus(status);
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreatedAt)
                .orderByDesc(VoucherOrder::getId);

        Page<VoucherOrder> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, Merchant> merchants = selectMerchants(page.getRecords().stream()
                .map(VoucherOrder::getMerchantId)
                .collect(Collectors.toSet()));
        Map<Long, Voucher> vouchers = selectVouchers(page.getRecords().stream()
                .map(VoucherOrder::getVoucherId)
                .collect(Collectors.toSet()));

        Page<VoucherOrderSummaryResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(order -> toSummary(order, merchants.get(order.getMerchantId()), vouchers.get(order.getVoucherId())))
                .toList());
        return result;
    }

    @Override
    public VoucherOrderDetailResponse getCurrentUserOrder(String orderNo, Long userId) {
        VoucherOrder order = orderMapper.selectOne(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getOrderNo, orderNo)
                .eq(VoucherOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Voucher order not found");
        }
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        Voucher voucher = voucherMapper.selectById(order.getVoucherId());
        return new VoucherOrderDetailResponse(
                order.getOrderNo(),
                order.getMerchantId(),
                merchant == null ? null : merchant.getName(),
                merchant == null ? null : merchant.getArea(),
                merchant == null ? null : merchant.getAddress(),
                merchant == null ? java.util.List.of() : ImagePathParser.split(merchant.getImages()),
                order.getVoucherId(),
                voucher == null ? null : voucher.getTitle(),
                voucher == null ? null : voucher.getSubtitle(),
                voucher == null ? null : voucher.getRules(),
                order.getPayAmountCent(),
                voucher == null ? null : voucher.getDiscountAmountCent(),
                voucher == null ? null : voucher.getType(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getClosedAt());
    }

    private VoucherOrderSummaryResponse toSummary(VoucherOrder order, Merchant merchant, Voucher voucher) {
        return new VoucherOrderSummaryResponse(
                order.getOrderNo(),
                order.getMerchantId(),
                merchant == null ? null : merchant.getName(),
                merchant == null ? java.util.List.of() : ImagePathParser.split(merchant.getImages()),
                order.getVoucherId(),
                voucher == null ? null : voucher.getTitle(),
                voucher == null ? null : voucher.getSubtitle(),
                order.getPayAmountCent(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getClosedAt());
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        boolean known = Arrays.stream(OrderStatus.values())
                .anyMatch(orderStatus -> orderStatus.code() == status);
        if (!known) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid voucher order status");
        }
    }

    private Map<Long, Merchant> selectMerchants(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return merchantMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    private Map<Long, Voucher> selectVouchers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return voucherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Voucher::getId, Function.identity()));
    }
}
