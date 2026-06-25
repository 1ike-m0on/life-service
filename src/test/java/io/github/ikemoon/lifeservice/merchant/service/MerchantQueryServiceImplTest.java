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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class MerchantQueryServiceImplTest {

    @Mock
    private MerchantCategoryMapper categoryMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private CacheClient cacheClient;

    @Mock
    private TwoLevelCacheClient twoLevelCacheClient;

    private MerchantQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper, cacheClient, twoLevelCacheClient);
    }

    @Test
    void listEnabledCategoriesUsesCacheContractAndMapperFallback() {
        MerchantCategory food = new MerchantCategory();
        food.setId(1L);
        food.setName("Food");
        MerchantCategory spa = new MerchantCategory();
        spa.setId(2L);
        spa.setName("Spa");
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(food, spa));
        when(twoLevelCacheClient.queryList(
                eq(CacheConstants.MERCHANT_CATEGORY_LIST_KEY),
                eq(MerchantCategory.class),
                anySupplier(),
                eq(Duration.ofMinutes(30))))
                .thenAnswer(invocation -> {
                    Supplier<List<MerchantCategory>> fallback = invocation.getArgument(2);
                    return fallback.get();
                });

        assertThat(service.listEnabledCategories()).containsExactly(food, spa);

        verify(categoryMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listEnabledCategoriesReturnsCachedEmptyListWithoutMapperFallback() {
        when(twoLevelCacheClient.queryList(
                eq(CacheConstants.MERCHANT_CATEGORY_LIST_KEY),
                eq(MerchantCategory.class),
                anySupplier(),
                eq(Duration.ofMinutes(30))))
                .thenReturn(List.of());

        assertThat(service.listEnabledCategories()).isEmpty();
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void pageMerchantsReturnsMapperPageWithCategoryAndKeywordFilters() {
        Merchant merchant = enabledMerchant(10L);
        Page<Merchant> page = Page.of(1, 10);
        page.setRecords(List.of(merchant));
        page.setTotal(1);
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<Merchant> result = service.pageMerchants(1L, "coffee", 1, 10);

        assertThat(result.getRecords()).containsExactly(merchant);
        assertThat(result.getTotal()).isEqualTo(1);
        ArgumentCaptor<Page<Merchant>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(merchantMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void pageMerchantsReturnsEmptyPageWhenCategoryAndKeywordAreAbsent() {
        Page<Merchant> page = Page.of(2, 5);
        page.setRecords(List.of());
        page.setTotal(0);
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<Merchant> result = service.pageMerchants(null, "   ", 2, 5);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        ArgumentCaptor<Page<Merchant>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(merchantMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5);
    }

    @Test
    void getMerchantReturnsCachedOrLoadedMerchant() {
        Merchant merchant = enabledMerchant(10L);
        whenQueryWithPassThrough(10L, merchant);

        assertThat(service.getMerchant(10L)).isSameAs(merchant);
    }

    @Test
    void getMerchantThrowsWhenCacheClientReturnsNull() {
        whenQueryWithPassThrough(10L, null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMerchant(10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getMerchantUsesFallbackToLoadEnabledMerchantOnCacheMiss() {
        Merchant merchant = enabledMerchant(10L);
        when(merchantMapper.selectById(10L)).thenReturn(merchant);
        whenQueryWithPassThroughInvokesFallback(10L);

        assertThat(service.getMerchant(10L)).isSameAs(merchant);

        verify(merchantMapper).selectById(10L);
    }

    @Test
    void getMerchantThrowsWhenFallbackFindsNoMerchant() {
        when(merchantMapper.selectById(10L)).thenReturn(null);
        whenQueryWithPassThroughInvokesFallback(10L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMerchant(10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getMerchantThrowsWhenFallbackFindsDisabledMerchant() {
        Merchant merchant = enabledMerchant(10L);
        merchant.setStatus(0);
        when(merchantMapper.selectById(10L)).thenReturn(merchant);
        whenQueryWithPassThroughInvokesFallback(10L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMerchant(10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private void whenQueryWithPassThrough(Long id, Merchant merchant) {
        when(cacheClient.queryWithPassThrough(
                eq(CacheConstants.MERCHANT_KEY_PREFIX),
                eq(id),
                eq(Merchant.class),
                anyFunction(),
                eq(Duration.ofMinutes(30)),
                eq(Duration.ofMinutes(2))))
                .thenReturn(merchant);
    }

    private void whenQueryWithPassThroughInvokesFallback(Long id) {
        when(cacheClient.queryWithPassThrough(
                eq(CacheConstants.MERCHANT_KEY_PREFIX),
                eq(id),
                eq(Merchant.class),
                anyFunction(),
                eq(Duration.ofMinutes(30)),
                eq(Duration.ofMinutes(2))))
                .thenAnswer(invocation -> {
                    Function<Long, Merchant> fallback = invocation.getArgument(3);
                    return fallback.apply(invocation.getArgument(1));
                });
    }

    private static Function<Long, Merchant> anyFunction() {
        return any();
    }

    private static Supplier<List<MerchantCategory>> anySupplier() {
        return any();
    }

    private static Merchant enabledMerchant(Long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName("Coffee Shop");
        merchant.setStatus(1);
        return merchant;
    }
}
