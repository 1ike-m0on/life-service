package io.github.ikemoon.lifeservice.order.service.payment;

import io.github.ikemoon.lifeservice.common.exception.ErrorCode;

public record VoucherOrderPaymentResult(
        boolean success,
        ErrorCode code,
        String message,
        String orderNo,
        Integer status,
        boolean idempotent) {

    public static VoucherOrderPaymentResult paid(String orderNo, int status) {
        return new VoucherOrderPaymentResult(true, null, null, orderNo, status, false);
    }

    public static VoucherOrderPaymentResult alreadyPaid(String orderNo, int status) {
        return new VoucherOrderPaymentResult(true, null, null, orderNo, status, true);
    }

    public static VoucherOrderPaymentResult fail(ErrorCode code, String message) {
        return new VoucherOrderPaymentResult(false, code, message, null, null, false);
    }
}
