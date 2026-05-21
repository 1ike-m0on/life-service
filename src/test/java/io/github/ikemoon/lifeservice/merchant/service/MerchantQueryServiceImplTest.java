package io.github.ikemoon.lifeservice.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.infrastructure.cache.CacheClient;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantCategoryMapper;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private MerchantQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper, cacheClient);
    }

    @Test
    void listEnabledCategoriesReturnsMapperResult() {
        MerchantCategory food = new MerchantCategory();
        food.setId(1L);
        food.setName("Food");
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(food));

        assertThat(service.listEnabledCategories()).containsExactly(food);
    }

    @Test
    void pageMerchantsReturnsMapperPage() {
        Merchant merchant = enabledMerchant(10L);
        Page<Merchant> page = Page.of(1, 10);
        page.setRecords(List.of(merchant));
        page.setTotal(1);
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<Merchant> result = service.pageMerchants(1L, "coffee", 1, 10);

        assertThat(result.getRecords()).containsExactly(merchant);
        assertThat(result.getTotal()).isEqualTo(1);
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

    private void whenQueryWithPassThrough(Long id, Merchant merchant) {
        when(cacheClient.queryWithPassThrough(
                anyString(),
                eq(id),
                eq(Merchant.class),
                anyFunction(),
                any(Duration.class),
                any(Duration.class)))
                .thenReturn(merchant);
    }

    private static Function<Long, Merchant> anyFunction() {
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
