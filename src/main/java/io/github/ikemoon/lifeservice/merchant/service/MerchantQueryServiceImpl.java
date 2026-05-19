package io.github.ikemoon.lifeservice.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantCategoryMapper;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MerchantQueryServiceImpl implements MerchantQueryService {

    private final MerchantCategoryMapper categoryMapper;
    private final MerchantMapper merchantMapper;

    public MerchantQueryServiceImpl(MerchantCategoryMapper categoryMapper, MerchantMapper merchantMapper) {
        this.categoryMapper = categoryMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public List<MerchantCategory> listEnabledCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<MerchantCategory>()
                .eq(MerchantCategory::getStatus, 1)
                .orderByAsc(MerchantCategory::getSortOrder)
                .orderByAsc(MerchantCategory::getId));
    }

    @Override
    public Page<Merchant> pageMerchants(Long categoryId, String keyword, long pageNo, long pageSize) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)
                .eq(categoryId != null, Merchant::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Merchant::getName, keyword)
                .orderByDesc(Merchant::getScore)
                .orderByDesc(Merchant::getSoldCount)
                .orderByAsc(Merchant::getId);
        return merchantMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
    }

    @Override
    public Merchant getMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null || Integer.valueOf(0).equals(merchant.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商户不存在");
        }
        return merchant;
    }
}
