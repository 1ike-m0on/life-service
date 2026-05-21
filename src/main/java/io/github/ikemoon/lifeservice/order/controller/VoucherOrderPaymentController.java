package io.github.ikemoon.lifeservice.order.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentResult;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<VoucherOrderPaymentResponse> pay(@PathVariable String orderNo) {
        VoucherOrderPaymentResult result = paymentService.pay(orderNo, UserContext.requiredUserId());
        if (!result.success()) {
            return ApiResponse.fail(result.code().name(), result.message());
        }
        return ApiResponse.ok(new VoucherOrderPaymentResponse(result.orderNo(), result.status(), result.idempotent()));
    }

    public record VoucherOrderPaymentResponse(String orderNo, Integer status, boolean idempotent) {
    }
}
