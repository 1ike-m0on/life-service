package io.github.ikemoon.lifeservice.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheClient;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheConstants;
import io.github.ikemoon.lifeservice.infrastructure.cache.TwoLevelCacheClient;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantCategoryMapper;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
public class MerchantQueryServiceImpl implements MerchantQueryService {

    private static final Duration MERCHANT_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration MERCHANT_NULL_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration MERCHANT_CATEGORY_REDIS_CACHE_TTL = Duration.ofMinutes(30);

    private final MerchantCategoryMapper categoryMapper;
    private final MerchantMapper merchantMapper;
    private final CacheClient cacheClient;
    private final TwoLevelCacheClient twoLevelCacheClient;

    public MerchantQueryServiceImpl(
            MerchantCategoryMapper categoryMapper,
            MerchantMapper merchantMapper,
            CacheClient cacheClient,
            TwoLevelCacheClient twoLevelCacheClient) {
        this.categoryMapper = categoryMapper;
        this.merchantMapper = merchantMapper;
        this.cacheClient = cacheClient;
        this.twoLevelCacheClient = twoLevelCacheClient;
    }

    @Override
    public List<MerchantCategory> listEnabledCategories() {
        return twoLevelCacheClient.queryList(
                CacheConstants.MERCHANT_CATEGORY_LIST_KEY,
                MerchantCategory.class,
                this::selectEnabledCategories,
                MERCHANT_CATEGORY_REDIS_CACHE_TTL);
    }

    private List<MerchantCategory> selectEnabledCategories() {
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
        Merchant merchant = cacheClient.queryWithPassThrough(
                CacheConstants.MERCHANT_KEY_PREFIX,
                id,
                Merchant.class,
                this::selectEnabledMerchantById,
                MERCHANT_CACHE_TTL,
                MERCHANT_NULL_CACHE_TTL);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Merchant not found");
        }
        return merchant;
    }

    private Merchant selectEnabledMerchantById(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null || Integer.valueOf(0).equals(merchant.getStatus())) {
            return null;
        }
        return merchant;
    }
}
