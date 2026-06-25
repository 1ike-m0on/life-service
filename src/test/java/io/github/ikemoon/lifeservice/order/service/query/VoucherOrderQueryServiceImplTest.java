package io.github.ikemoon.lifeservice.order.service.query;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.mapper.MerchantMapper;
import io.github.ikemoon.lifeservice.order.entity.VoucherOrder;
import io.github.ikemoon.lifeservice.order.enums.OrderStatus;
import io.github.ikemoon.lifeservice.order.mapper.VoucherOrderMapper;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderDetailResponse;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderSummaryResponse;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.mapper.VoucherMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VoucherOrderQueryServiceImplTest {

    @Mock
    private VoucherOrderMapper orderMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private VoucherMapper voucherMapper;

    private VoucherOrderQueryServiceImpl service;

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "VoucherOrderQueryServiceImplTest"),
                VoucherOrder.class);
    }

    @BeforeEach
    void setUp() {
        service = new VoucherOrderQueryServiceImpl(orderMapper, merchantMapper, voucherMapper);
    }

    @Test
    void pageCurrentUserOrdersMapsSummariesAndPaginationMetadata() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 20, 12, 0);
        VoucherOrder paidOrder = order(
                100L,
                "LSO202605200000000001",
                200L,
                300L,
                990L,
                OrderStatus.PAID,
                createdAt,
                createdAt.plusMinutes(2),
                null);
        VoucherOrder closedOrder = order(
                101L,
                "LSO202605200000000002",
                201L,
                301L,
                1990L,
                OrderStatus.CLOSED,
                createdAt.plusMinutes(5),
                null,
                createdAt.plusMinutes(20));
        Page<VoucherOrder> rawPage = new Page<>(2, 5);
        rawPage.setTotal(2);
        rawPage.setRecords(List.of(paidOrder, closedOrder));
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);
        when(merchantMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                merchant(201L, "Tea House", "/img/tea.png"),
                merchant(200L, "Moonlight Coffee", " /img/coffee-1.png, ,/img/coffee-2.png ")));
        when(voucherMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                voucher(301L, "Dinner Voucher", "Weekday only", "Use before 21:00", 500L, 2),
                voucher(300L, "Coffee Voucher", "Hot drink set", "One per user", 300L, 1)));

        Page<VoucherOrderSummaryResponse> result = service.pageCurrentUserOrders(
                10L,
                OrderStatus.PAID.code(),
                2,
                5);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        VoucherOrderSummaryResponse first = result.getRecords().get(0);
        assertThat(first.orderNo()).isEqualTo("LSO202605200000000001");
        assertThat(first.merchantId()).isEqualTo(200L);
        assertThat(first.merchantName()).isEqualTo("Moonlight Coffee");
        assertThat(first.merchantImages()).containsExactly("/img/coffee-1.png", "/img/coffee-2.png");
        assertThat(first.voucherId()).isEqualTo(300L);
        assertThat(first.voucherTitle()).isEqualTo("Coffee Voucher");
        assertThat(first.voucherSubtitle()).isEqualTo("Hot drink set");
        assertThat(first.payAmountCent()).isEqualTo(990L);
        assertThat(first.status()).isEqualTo(OrderStatus.PAID.code());
        assertThat(first.createdAt()).isEqualTo(createdAt);
        assertThat(first.paidAt()).isEqualTo(createdAt.plusMinutes(2));
        assertThat(first.closedAt()).isNull();
        VoucherOrderSummaryResponse second = result.getRecords().get(1);
        assertThat(second.orderNo()).isEqualTo("LSO202605200000000002");
        assertThat(second.merchantName()).isEqualTo("Tea House");
        assertThat(second.merchantImages()).containsExactly("/img/tea.png");
        assertThat(second.voucherTitle()).isEqualTo("Dinner Voucher");
        assertThat(second.payAmountCent()).isEqualTo(1990L);
        assertThat(second.status()).isEqualTo(OrderStatus.CLOSED.code());
        assertThat(second.closedAt()).isEqualTo(createdAt.plusMinutes(20));
        assertPageQuery(OrderStatus.PAID.code(), 2, 5);
        assertBatchIds(merchantMapper, 200L, 201L);
        assertBatchIds(voucherMapper, 300L, 301L);
    }

    @Test
    void pageCurrentUserOrdersReturnsEmptyPageWithoutMetadataLookups() {
        Page<VoucherOrder> rawPage = new Page<>(1, 10);
        rawPage.setTotal(0);
        rawPage.setRecords(List.of());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(rawPage);

        Page<VoucherOrderSummaryResponse> result = service.pageCurrentUserOrders(10L, null, 1, 10);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        assertPageQuery(null, 1, 10);
        verify(merchantMapper, never()).selectBatchIds(anyCollection());
        verify(voucherMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    void pageCurrentUserOrdersRejectsUnknownStatus() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.pageCurrentUserOrders(10L, 99, 1, 10))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(orderMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void getCurrentUserOrderMapsDetail() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 20, 12, 30);
        VoucherOrder order = order(
                100L,
                "LSO202605200000000001",
                200L,
                300L,
                990L,
                OrderStatus.PAID,
                createdAt,
                createdAt.plusMinutes(3),
                null);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(merchantMapper.selectById(200L)).thenReturn(merchant(200L, "Moonlight Coffee", "/img/a.png,/img/b.png"));
        when(voucherMapper.selectById(300L)).thenReturn(voucher(
                300L,
                "Coffee Voucher",
                "Hot drink set",
                "One per user",
                300L,
                1));

        VoucherOrderDetailResponse result = service.getCurrentUserOrder("LSO202605200000000001", 10L);

        assertThat(result.orderNo()).isEqualTo("LSO202605200000000001");
        assertThat(result.merchantId()).isEqualTo(200L);
        assertThat(result.merchantName()).isEqualTo("Moonlight Coffee");
        assertThat(result.merchantArea()).isEqualTo("Haidian");
        assertThat(result.merchantAddress()).isEqualTo("No. 1 Road");
        assertThat(result.merchantImages()).containsExactly("/img/a.png", "/img/b.png");
        assertThat(result.voucherId()).isEqualTo(300L);
        assertThat(result.voucherTitle()).isEqualTo("Coffee Voucher");
        assertThat(result.voucherSubtitle()).isEqualTo("Hot drink set");
        assertThat(result.voucherRules()).isEqualTo("One per user");
        assertThat(result.payAmountCent()).isEqualTo(990L);
        assertThat(result.discountAmountCent()).isEqualTo(300L);
        assertThat(result.voucherType()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID.code());
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.paidAt()).isEqualTo(createdAt.plusMinutes(3));
        assertThat(result.closedAt()).isNull();
        assertDetailQuery();
        verify(merchantMapper).selectById(200L);
        verify(voucherMapper).selectById(300L);
    }

    @Test
    void getCurrentUserOrderReturnsNullMetadataWhenReferencesAreMissing() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 20, 13, 0);
        VoucherOrder order = order(
                100L,
                "LSO202605200000000001",
                200L,
                300L,
                990L,
                OrderStatus.CLOSED,
                createdAt,
                null,
                createdAt.plusMinutes(15));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(merchantMapper.selectById(200L)).thenReturn(null);
        when(voucherMapper.selectById(300L)).thenReturn(null);

        VoucherOrderDetailResponse result = service.getCurrentUserOrder("LSO202605200000000001", 10L);

        assertThat(result.merchantName()).isNull();
        assertThat(result.merchantArea()).isNull();
        assertThat(result.merchantAddress()).isNull();
        assertThat(result.merchantImages()).isEmpty();
        assertThat(result.voucherTitle()).isNull();
        assertThat(result.voucherSubtitle()).isNull();
        assertThat(result.voucherRules()).isNull();
        assertThat(result.discountAmountCent()).isNull();
        assertThat(result.voucherType()).isNull();
        assertThat(result.payAmountCent()).isEqualTo(990L);
        assertThat(result.status()).isEqualTo(OrderStatus.CLOSED.code());
        assertThat(result.closedAt()).isEqualTo(createdAt.plusMinutes(15));
    }

    @Test
    void getCurrentUserOrderThrowsNotFoundWhenMissingOrOwnedByAnotherUser() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getCurrentUserOrder("LSO202605200000000001", 10L))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertDetailQuery();
        verify(merchantMapper, never()).selectById(any());
        verify(voucherMapper, never()).selectById(any());
    }

    private void assertPageQuery(Integer status, long pageNo, long pageSize) {
        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(pageNo);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(pageSize);
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("user_id", "created_at DESC", "id DESC");
        if (status == null) {
            assertThat(sqlSegment).doesNotContain("status");
        } else {
            assertThat(sqlSegment).contains("status");
        }
    }

    private void assertDetailQuery() {
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("order_no", "user_id");
    }

    private static void assertBatchIds(MerchantMapper mapper, Long... expectedIds) {
        ArgumentCaptor<Collection> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).selectBatchIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(List.of(expectedIds));
    }

    private static void assertBatchIds(VoucherMapper mapper, Long... expectedIds) {
        ArgumentCaptor<Collection> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).selectBatchIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrderElementsOf(List.of(expectedIds));
    }

    private static VoucherOrder order(
            Long id,
            String orderNo,
            Long merchantId,
            Long voucherId,
            Long payAmountCent,
            OrderStatus status,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            LocalDateTime closedAt) {
        VoucherOrder order = new VoucherOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(10L);
        order.setMerchantId(merchantId);
        order.setVoucherId(voucherId);
        order.setPayAmountCent(payAmountCent);
        order.setStatus(status.code());
        order.setCreatedAt(createdAt);
        order.setPaidAt(paidAt);
        order.setClosedAt(closedAt);
        return order;
    }

    private static Merchant merchant(Long id, String name, String images) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName(name);
        merchant.setImages(images);
        merchant.setArea("Haidian");
        merchant.setAddress("No. 1 Road");
        return merchant;
    }

    private static Voucher voucher(
            Long id,
            String title,
            String subtitle,
            String rules,
            Long discountAmountCent,
            Integer type) {
        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setTitle(title);
        voucher.setSubtitle(subtitle);
        voucher.setRules(rules);
        voucher.setDiscountAmountCent(discountAmountCent);
        voucher.setType(type);
        return voucher;
    }
}
