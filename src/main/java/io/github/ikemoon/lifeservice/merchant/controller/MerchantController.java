package io.github.ikemoon.lifeservice.merchant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.api.PageResponse;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.service.MerchantQueryService;
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
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantQueryService merchantQueryService;

    public MerchantController(MerchantQueryService merchantQueryService) {
        this.merchantQueryService = merchantQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Merchant>> pageMerchants(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize) {
        Page<Merchant> page = merchantQueryService.pageMerchants(categoryId, keyword, pageNo, pageSize);
        return ApiResponse.ok(new PageResponse<>(page.getRecords(), page.getTotal(), pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Merchant> getMerchant(@PathVariable Long id) {
        return ApiResponse.ok(merchantQueryService.getMerchant(id));
    }
}
