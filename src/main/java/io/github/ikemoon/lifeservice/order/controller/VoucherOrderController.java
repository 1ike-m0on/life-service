package io.github.ikemoon.lifeservice.order.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitFailureStrategy;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimitType;
import io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @RateLimiter(
            key = "life:rate:flash-sale:order:",
            window = 1,
            limit = 1000,
            message = "Flash sale traffic is too high, please try again later",
            type = RateLimitType.GLOBAL,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    @RateLimiter(
            key = "life:rate:flash-sale:order:",
            window = 10,
            limit = 3,
            message = "Too many requests, please try again later",
            type = RateLimitType.USER,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    @RateLimiter(
            key = "life:rate:flash-sale:order:",
            window = 10,
            limit = 30,
            message = "Too many requests, please try again later",
            type = RateLimitType.IP,
            failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
    public ApiResponse<String> seckill(@PathVariable Long voucherId) {
        FlashSaleOrderResult result = flashSaleOrderService.seckill(voucherId, UserContext.requiredUserId());
        return toResponse(result);
    }

    private ApiResponse<String> toResponse(FlashSaleOrderResult result) {
        if (!result.success()) {
            return ApiResponse.fail(result.code().name(), result.message());
        }
        return ApiResponse.ok(result.orderNo());
    }
}
