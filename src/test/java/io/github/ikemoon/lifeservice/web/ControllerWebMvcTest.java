package io.github.ikemoon.lifeservice.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.common.security.AuthInterceptor;
import io.github.ikemoon.lifeservice.common.security.AuthTokenService;
import io.github.ikemoon.lifeservice.common.security.UserPrincipal;
import io.github.ikemoon.lifeservice.common.exception.GlobalExceptionHandler;
import io.github.ikemoon.lifeservice.merchant.controller.MerchantController;
import io.github.ikemoon.lifeservice.merchant.controller.MerchantCategoryController;
import io.github.ikemoon.lifeservice.merchant.entity.Merchant;
import io.github.ikemoon.lifeservice.merchant.entity.MerchantCategory;
import io.github.ikemoon.lifeservice.merchant.service.MerchantQueryService;
import io.github.ikemoon.lifeservice.order.controller.VoucherOrderController;
import io.github.ikemoon.lifeservice.order.controller.VoucherOrderPaymentController;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentResult;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentService;
import io.github.ikemoon.lifeservice.user.controller.AuthController;
import io.github.ikemoon.lifeservice.user.service.AuthResponse;
import io.github.ikemoon.lifeservice.user.service.CurrentUserResponse;
import io.github.ikemoon.lifeservice.user.service.UserAuthService;
import io.github.ikemoon.lifeservice.voucher.controller.VoucherController;
import io.github.ikemoon.lifeservice.voucher.entity.Voucher;
import io.github.ikemoon.lifeservice.voucher.controller.FlashSaleVoucherWarmupController;
import io.github.ikemoon.lifeservice.voucher.service.FlashSaleVoucherWarmupResult;
import io.github.ikemoon.lifeservice.voucher.service.FlashSaleVoucherWarmupService;
import io.github.ikemoon.lifeservice.voucher.service.VoucherQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ControllerWebMvcTest {

    private static final String TOKEN = "token-abc";
    private static final UserPrincipal USER = new UserPrincipal(10L, "demo@life.local", "demo");

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserAuthService userAuthService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private MerchantQueryService merchantQueryService;

    @Mock
    private FlashSaleVoucherWarmupService warmupService;

    @Mock
    private VoucherQueryService voucherQueryService;

    @Mock
    private FlashSaleOrderService flashSaleOrderService;

    @Mock
    private VoucherOrderPaymentService paymentService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(userAuthService),
                        new MerchantController(merchantQueryService),
                        new MerchantCategoryController(merchantQueryService),
                        new VoucherController(voucherQueryService),
                        new FlashSaleVoucherWarmupController(warmupService),
                        new VoucherOrderController(flashSaleOrderService),
                        new VoucherOrderPaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addMappedInterceptors(new String[]{
                                "/api/v1/auth/me",
                                "/api/v1/auth/logout",
                                "/api/v1/flash-sale-vouchers/*/orders",
                                "/api/v1/voucher-orders/*/payment"
                        },
                        new AuthInterceptor(authTokenService))
                .build();
    }

    @Test
    void loginReturnsUnifiedSuccessResponse() throws Exception {
        when(userAuthService.login(any()))
                .thenReturn(new AuthResponse(TOKEN, "Bearer", 10L, "demo@life.local", "demo"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "demo@life.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.token").value(TOKEN))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userId").value(10));
    }

    @Test
    void loginRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "bad-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()));
    }

    @Test
    void currentUserRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void currentUserReturnsUserWhenTokenIsValid() throws Exception {
        givenValidToken();
        when(userAuthService.currentUser())
                .thenReturn(new CurrentUserResponse(10L, "demo@life.local", "demo"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.email").value("demo@life.local"));
    }

    @Test
    void pageMerchantsBindsQueryParametersAndReturnsPage() throws Exception {
        Page<Merchant> page = new Page<>(2, 5);
        page.setRecords(List.of(merchant(1L, "Moonlight Coffee")));
        page.setTotal(12);
        when(merchantQueryService.pageMerchants(1L, "coffee", 2, 5)).thenReturn(page);

        mockMvc.perform(get("/api/v1/merchants")
                        .param("categoryId", "1")
                        .param("keyword", "coffee")
                        .param("pageNo", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].name").value("Moonlight Coffee"));

        verify(merchantQueryService).pageMerchants(1L, "coffee", 2, 5);
    }

    @Test
    void getMerchantReturnsMerchantDetail() throws Exception {
        when(merchantQueryService.getMerchant(1L)).thenReturn(merchant(1L, "Moonlight Coffee"));

        mockMvc.perform(get("/api/v1/merchants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Moonlight Coffee"));
    }

    @Test
    void listMerchantCategoriesReturnsEnabledCategories() throws Exception {
        MerchantCategory category = new MerchantCategory();
        category.setId(1L);
        category.setName("Coffee");
        category.setSortOrder(10);
        category.setStatus(1);
        when(merchantQueryService.listEnabledCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/api/v1/merchant-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Coffee"));
    }

    @Test
    void listMerchantVouchersReturnsVoucherList() throws Exception {
        Voucher voucher = new Voucher();
        voucher.setId(1001L);
        voucher.setMerchantId(1L);
        voucher.setTitle("Coffee Flash Sale 19.9");
        voucher.setPayAmountCent(1990L);
        voucher.setDiscountAmountCent(2000L);
        voucher.setType(2);
        voucher.setStatus(1);
        when(voucherQueryService.listMerchantVouchers(1L)).thenReturn(List.of(voucher));

        mockMvc.perform(get("/api/v1/merchants/1/vouchers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].title").value("Coffee Flash Sale 19.9"));
    }

    @Test
    void logoutRevokesBearerTokenWhenTokenIsValid() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"));

        verify(userAuthService).logout(TOKEN);
    }

    @Test
    void warmupReturnsWarmupResult() throws Exception {
        when(warmupService.warmUp(1001L)).thenReturn(new FlashSaleVoucherWarmupResult(
                1001L,
                12000,
                2,
                3600,
                "life:flash-sale:voucher:1001",
                "life:flash-sale:stock:1001",
                "life:flash-sale:users:1001"));

        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/warmup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.voucherId").value(1001))
                .andExpect(jsonPath("$.data.stock").value(12000));
    }

    @Test
    void flashSaleOrderRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void flashSaleOrderReturnsOrderNoWhenQualified() throws Exception {
        givenValidToken();
        when(flashSaleOrderService.seckill(1001L, 10L))
                .thenReturn(FlashSaleOrderResult.success("LSO202605220000000001"));

        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/orders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("LSO202605220000000001"));
    }

    @Test
    void flashSaleOrderReturnsBusinessFailureAsApiResponse() throws Exception {
        givenValidToken();
        when(flashSaleOrderService.seckill(1001L, 10L))
                .thenReturn(FlashSaleOrderResult.fail(
                        ErrorCode.FLASH_SALE_DUPLICATE_ORDER,
                        "duplicate order"));

        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/orders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FLASH_SALE_DUPLICATE_ORDER.name()))
                .andExpect(jsonPath("$.message").value("duplicate order"));
    }

    @Test
    void paymentReturnsPaidResponse() throws Exception {
        givenValidToken();
        when(paymentService.pay("LSO202605220000000001", 10L))
                .thenReturn(VoucherOrderPaymentResult.paid("LSO202605220000000001", 2));

        mockMvc.perform(post("/api/v1/voucher-orders/LSO202605220000000001/payment")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("LSO202605220000000001"))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.idempotent").value(false));
    }

    @Test
    void paymentReturnsClosedBusinessFailureAsApiResponse() throws Exception {
        givenValidToken();
        when(paymentService.pay("LSO202605220000000001", 10L))
                .thenReturn(VoucherOrderPaymentResult.fail(ErrorCode.ORDER_CLOSED, "order closed"));

        mockMvc.perform(post("/api/v1/voucher-orders/LSO202605220000000001/payment")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_CLOSED.name()))
                .andExpect(jsonPath("$.message").value("order closed"));
    }

    private void givenValidToken() {
        when(authTokenService.resolve(TOKEN)).thenReturn(Optional.of(USER));
    }

    private static Merchant merchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setCategoryId(1L);
        merchant.setName(name);
        merchant.setArea("Chaoyang");
        merchant.setAddress("88 Lake Road");
        merchant.setAvgPriceCent(3600L);
        merchant.setSoldCount(1280);
        merchant.setCommentCount(342);
        merchant.setScore(47);
        merchant.setOpenHours("08:00-22:30");
        merchant.setStatus(1);
        return merchant;
    }
}
