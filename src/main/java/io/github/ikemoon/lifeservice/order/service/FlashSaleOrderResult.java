package io.github.ikemoon.lifeservice.order.service;

import io.github.ikemoon.lifeservice.common.exception.ErrorCode;

public record FlashSaleOrderResult(boolean success, ErrorCode code, String message, String orderNo) {

    public static FlashSaleOrderResult success(String orderNo) {
        return new FlashSaleOrderResult(true, null, null, orderNo);
    }

    public static FlashSaleOrderResult fail(ErrorCode code, String message) {
        return new FlashSaleOrderResult(false, code, message, null);
    }
}
