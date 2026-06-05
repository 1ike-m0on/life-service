package io.github.ikemoon.lifeservice.order.response;

import java.time.LocalDateTime;
import java.util.List;

public record VoucherOrderSummaryResponse(
        String orderNo,
        Long merchantId,
        String merchantName,
        List<String> merchantImages,
        Long voucherId,
        String voucherTitle,
        String voucherSubtitle,
        Long payAmountCent,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt) {
}
