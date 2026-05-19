package io.github.ikemoon.lifeservice.voucher.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.service.VoucherQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/vouchers")
public class VoucherController {

    private final VoucherQueryService voucherQueryService;

    public VoucherController(VoucherQueryService voucherQueryService) {
        this.voucherQueryService = voucherQueryService;
    }

    @GetMapping
    public ApiResponse<List<Voucher>> listMerchantVouchers(@PathVariable Long merchantId) {
        return ApiResponse.ok(voucherQueryService.listMerchantVouchers(merchantId));
    }
}
