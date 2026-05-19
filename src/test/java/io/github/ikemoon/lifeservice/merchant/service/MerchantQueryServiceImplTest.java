package io.github.ikemoon.lifeservice.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantCategoryMapper;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantQueryServiceImplTest {

    @Mock
    private MerchantCategoryMapper categoryMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Test
    void listEnabledCategoriesReturnsMapperResult() {
        MerchantCategory food = new MerchantCategory();
        food.setId(1L);
        food.setName("Food");
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(food));

        MerchantQueryServiceImpl service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper);

        assertThat(service.listEnabledCategories()).containsExactly(food);
    }

    @Test
    void pageMerchantsReturnsMapperPage() {
        Merchant merchant = enabledMerchant(10L);
        Page<Merchant> page = Page.of(1, 10);
        page.setRecords(List.of(merchant));
        page.setTotal(1);
        when(merchantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        MerchantQueryServiceImpl service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper);

        Page<Merchant> result = service.pageMerchants(1L, "coffee", 1, 10);

        assertThat(result.getRecords()).containsExactly(merchant);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void getMerchantReturnsEnabledMerchant() {
        Merchant merchant = enabledMerchant(10L);
        when(merchantMapper.selectById(10L)).thenReturn(merchant);

        MerchantQueryServiceImpl service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper);

        assertThat(service.getMerchant(10L)).isSameAs(merchant);
    }

    @Test
    void getMerchantThrowsWhenMerchantMissing() {
        when(merchantMapper.selectById(10L)).thenReturn(null);

        MerchantQueryServiceImpl service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMerchant(10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getMerchantThrowsWhenMerchantDisabled() {
        Merchant merchant = enabledMerchant(10L);
        merchant.setStatus(0);
        when(merchantMapper.selectById(10L)).thenReturn(merchant);

        MerchantQueryServiceImpl service = new MerchantQueryServiceImpl(categoryMapper, merchantMapper);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getMerchant(10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private static Merchant enabledMerchant(Long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName("Coffee Shop");
        merchant.setStatus(1);
        return merchant;
    }
}
