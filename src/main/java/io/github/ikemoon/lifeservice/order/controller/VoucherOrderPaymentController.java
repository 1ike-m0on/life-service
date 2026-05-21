package io.github.ikemoon.lifeservice.order.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentResult;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voucher-orders")
public class VoucherOrderPaymentController {

    private final VoucherOrderPaymentService paymentService;

    public VoucherOrderPaymentController(VoucherOrderPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/payment")
    public ApiResponse<VoucherOrderPaymentResponse> pay(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        VoucherOrderPaymentResult result = paymentService.pay(orderNo, userId);
        if (!result.success()) {
            return ApiResponse.fail(result.code().name(), result.message());
        }
        return ApiResponse.ok(new VoucherOrderPaymentResponse(result.orderNo(), result.status(), result.idempotent()));
    }

    public record VoucherOrderPaymentResponse(String orderNo, Integer status, boolean idempotent) {
    }
}
