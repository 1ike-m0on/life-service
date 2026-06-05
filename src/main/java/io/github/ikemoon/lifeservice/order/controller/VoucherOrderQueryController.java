package io.github.ikemoon.lifeservice.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderDetailResponse;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderSummaryResponse;
import io.github.ikemoon.lifeservice.order.service.query.VoucherOrderQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class VoucherOrderQueryController {

    private final VoucherOrderQueryService voucherOrderQueryService;

    public VoucherOrderQueryController(VoucherOrderQueryService voucherOrderQueryService) {
        this.voucherOrderQueryService = voucherOrderQueryService;
    }

    @GetMapping("/users/me/voucher-orders")
    public ApiResponse<PageResponse<VoucherOrderSummaryResponse>> pageMyOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize) {
        Long userId = UserContext.requiredUserId();
        Page<VoucherOrderSummaryResponse> page = voucherOrderQueryService.pageCurrentUserOrders(userId, status, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }

    @GetMapping("/voucher-orders/{orderNo}")
    public ApiResponse<VoucherOrderDetailResponse> getOrder(@PathVariable String orderNo) {
        Long userId = UserContext.requiredUserId();
        return ApiResponse.ok(voucherOrderQueryService.getCurrentUserOrder(orderNo, userId));
    }
}
