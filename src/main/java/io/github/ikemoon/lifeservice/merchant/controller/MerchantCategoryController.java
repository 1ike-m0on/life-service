package io.github.ikemoon.lifeservice.merchant.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.service.MerchantQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant-categories")
public class MerchantCategoryController {

    private final MerchantQueryService merchantQueryService;

    public MerchantCategoryController(MerchantQueryService merchantQueryService) {
        this.merchantQueryService = merchantQueryService;
    }

    @GetMapping
    public ApiResponse<List<MerchantCategory>> listCategories() {
        return ApiResponse.ok(merchantQueryService.listEnabledCategories());
    }
}
