package io.github.ikemoon.lifeservice.order.response;

import java.time.LocalDateTime;
import java.util.List;

public record VoucherOrderDetailResponse(
        String orderNo,
        Long merchantId,
        String merchantName,
        String merchantArea,
        String merchantAddress,
        List<String> merchantImages,
        Long voucherId,
        String voucherTitle,
        String voucherSubtitle,
        String voucherRules,
        Long payAmountCent,
        Long discountAmountCent,
        Integer voucherType,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt) {
}
