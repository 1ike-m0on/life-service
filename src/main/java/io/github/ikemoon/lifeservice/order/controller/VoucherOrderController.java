package io.github.ikemoon.lifeservice.order.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flash-sale-vouchers")
public class VoucherOrderController {

    private final FlashSaleOrderService flashSaleOrderService;

    public VoucherOrderController(FlashSaleOrderService flashSaleOrderService) {
        this.flashSaleOrderService = flashSaleOrderService;
    }

    @PostMapping("/{voucherId}/orders")
    public ApiResponse<String> seckill(
            @PathVariable Long voucherId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        FlashSaleOrderResult result = flashSaleOrderService.seckill(voucherId, userId);
        return toResponse(result);
    }

    private ApiResponse<String> toResponse(FlashSaleOrderResult result) {
        if (!result.success()) {
            return ApiResponse.fail(result.code().name(), result.message());
        }
        return ApiResponse.ok(result.orderNo());
    }
}
