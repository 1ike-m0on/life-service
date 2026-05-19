package io.github.ikemoon.lifeservice.merchant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;

import java.util.List;

public interface MerchantQueryService {

    List<MerchantCategory> listEnabledCategories();

    Page<Merchant> pageMerchants(Long categoryId, String keyword, long pageNo, long pageSize);

    Merchant getMerchant(Long id);
}
