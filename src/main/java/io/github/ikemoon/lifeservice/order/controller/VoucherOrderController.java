package io.github.ikemoon.lifeservice.order.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
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
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(flashSaleOrderService.seckill(voucherId, userId));
    }
}
