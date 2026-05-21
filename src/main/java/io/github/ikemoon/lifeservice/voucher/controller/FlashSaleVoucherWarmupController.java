package io.github.ikemoon.lifeservice.voucher.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.voucher.service.FlashSaleVoucherWarmupResult;
import io.github.ikemoon.lifeservice.voucher.service.FlashSaleVoucherWarmupService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flash-sale-vouchers")
public class FlashSaleVoucherWarmupController {

    private final FlashSaleVoucherWarmupService warmupService;

    public FlashSaleVoucherWarmupController(FlashSaleVoucherWarmupService warmupService) {
        this.warmupService = warmupService;
    }

    @PostMapping("/{voucherId}/warmup")
    public ApiResponse<FlashSaleVoucherWarmupResult> warmUp(@PathVariable Long voucherId) {
        return ApiResponse.ok(warmupService.warmUp(voucherId));
    }
}
