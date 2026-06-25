package io.github.ikemoon.lifeservice.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
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
import io.github.ikemoon.lifeservice.note.controller.MerchantNoteController;
import io.github.ikemoon.lifeservice.note.controller.MyNoteController;
import io.github.ikemoon.lifeservice.note.controller.NoteCommentController;
import io.github.ikemoon.lifeservice.note.controller.NoteController;
import io.github.ikemoon.lifeservice.note.controller.NoteFavoriteController;
import io.github.ikemoon.lifeservice.note.response.NoteCardResponse;
import io.github.ikemoon.lifeservice.note.response.NoteCommentResponse;
import io.github.ikemoon.lifeservice.note.response.NoteDetailResponse;
import io.github.ikemoon.lifeservice.note.response.NoteFavoriteResponse;
import io.github.ikemoon.lifeservice.note.service.NoteCommentService;
import io.github.ikemoon.lifeservice.note.service.NoteCommandService;
import io.github.ikemoon.lifeservice.note.service.NoteFavoriteService;
import io.github.ikemoon.lifeservice.note.service.NoteQueryService;
import io.github.ikemoon.lifeservice.order.controller.VoucherOrderController;
import io.github.ikemoon.lifeservice.order.controller.VoucherOrderPaymentController;
import io.github.ikemoon.lifeservice.order.controller.VoucherOrderQueryController;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderDetailResponse;
import io.github.ikemoon.lifeservice.order.response.VoucherOrderSummaryResponse;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderResult;
import io.github.ikemoon.lifeservice.order.service.FlashSaleOrderService;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentResult;
import io.github.ikemoon.lifeservice.order.service.payment.VoucherOrderPaymentService;
import io.github.ikemoon.lifeservice.order.service.query.VoucherOrderQueryService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Mock
    private VoucherOrderQueryService voucherOrderQueryService;

    @Mock
    private NoteQueryService noteQueryService;

    @Mock
    private NoteCommandService noteCommandService;

    @Mock
    private NoteCommentService noteCommentService;

    @Mock
    private NoteFavoriteService noteFavoriteService;

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
                        new VoucherOrderPaymentController(paymentService),
                        new VoucherOrderQueryController(voucherOrderQueryService),
                        new NoteController(noteQueryService),
                        new MerchantNoteController(noteQueryService),
                        new MyNoteController(noteQueryService, noteCommandService),
                        new NoteCommentController(noteCommentService),
                        new NoteFavoriteController(noteFavoriteService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addMappedInterceptors(new String[]{
                                "/api/v1/auth/me",
                                "/api/v1/auth/logout",
                                "/api/v1/flash-sale-vouchers/*/orders",
                                "/api/v1/users/me/notes",
                                "/api/v1/users/me/notes/*",
                                "/api/v1/users/me/notes/*/comments",
                                "/api/v1/users/me/notes/*/favorite",
                                "/api/v1/users/me/note-comments/*",
                                "/api/v1/users/me/favorite-notes",
                                "/api/v1/users/me/voucher-orders",
                                "/api/v1/voucher-orders/*",
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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()));

        verifyNoInteractions(userAuthService);
    }

    @Test
    void currentUserRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void currentUserRejectsExpiredTokenBeforeServiceCall() throws Exception {
        when(authTokenService.resolve(TOKEN)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(userAuthService);
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
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].name").value("Moonlight Coffee"));

        verify(merchantQueryService).pageMerchants(1L, "coffee", 2, 5);
    }

    @Test
    void pageMerchantsUsesDefaultPaginationWhenQueryIsOmitted() throws Exception {
        Page<Merchant> page = new Page<>(1, 10);
        page.setRecords(List.of(merchant(2L, "Harbor Noodles")));
        page.setTotal(1);
        when(merchantQueryService.pageMerchants(null, null, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.records[0].id").value(2))
                .andExpect(jsonPath("$.data.records[0].name").value("Harbor Noodles"));

        verify(merchantQueryService).pageMerchants(null, null, 1, 10);
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
    void getMerchantBusinessExceptionReturnsApiResponse() throws Exception {
        when(merchantQueryService.getMerchant(404L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "merchant not found"));

        mockMvc.perform(get("/api/v1/merchants/404"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value("merchant not found"));
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
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].title").value("Coffee Flash Sale 19.9"));

        verify(voucherQueryService).listMerchantVouchers(1L);
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
    void logoutRejectsMalformedAuthorizationHeaderBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Token " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(authTokenService, userAuthService);
    }

    @Test
    void logoutRejectsExpiredTokenBeforeServiceCall() throws Exception {
        when(authTokenService.resolve(TOKEN)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(userAuthService);
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
    void warmupBusinessExceptionReturnsApiResponse() throws Exception {
        when(warmupService.warmUp(1001L))
                .thenThrow(new BusinessException(ErrorCode.FLASH_SALE_NOT_READY, "warmup source not ready"));

        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/warmup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FLASH_SALE_NOT_READY.name()))
                .andExpect(jsonPath("$.message").value("warmup source not ready"));
    }

    @Test
    void flashSaleOrderRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void flashSaleOrderRejectsExpiredTokenBeforeServiceCall() throws Exception {
        when(authTokenService.resolve(TOKEN)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/flash-sale-vouchers/1001/orders")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(flashSaleOrderService);
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
    void paymentReturnsAlreadyPaidAsIdempotentResponse() throws Exception {
        givenValidToken();
        when(paymentService.pay("LSO202605220000000001", 10L))
                .thenReturn(VoucherOrderPaymentResult.alreadyPaid("LSO202605220000000001", 2));

        mockMvc.perform(post("/api/v1/voucher-orders/LSO202605220000000001/payment")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("LSO202605220000000001"))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.idempotent").value(true));
    }

    @Test
    void paymentRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/voucher-orders/LSO202605220000000001/payment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(paymentService);
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

    @Test
    void pageNotesReturnsPublicNoteCards() throws Exception {
        Page<NoteCardResponse> page = new Page<>(3, 7);
        page.setTotal(1);
        page.setRecords(List.of(noteCard(1L, "午后咖啡")));
        when(noteQueryService.pagePublicNotes(3, 7)).thenReturn(page);

        mockMvc.perform(get("/api/v1/notes")
                        .param("pageNo", "3")
                        .param("pageSize", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNo").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(7))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].merchantName").value("Moonlight Coffee"))
                .andExpect(jsonPath("$.data.records[0].images[0]").value("/assets/merchants/coffee/moonlight-cover.jpg"));

        verify(noteQueryService).pagePublicNotes(3, 7);
    }

    @Test
    void getNoteReturnsPublicNoteDetail() throws Exception {
        when(noteQueryService.getVisibleNote(1L)).thenReturn(new NoteDetailResponse(
                1L,
                1L,
                "Moonlight Coffee",
                2001L,
                "Demo User 2001",
                null,
                "午后咖啡",
                "拿铁奶香很稳",
                5,
                List.of("/assets/merchants/coffee/moonlight-cover.jpg"),
                262,
                3,
                2,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                LocalDateTime.of(2026, 5, 20, 12, 0)));

        mockMvc.perform(get("/api/v1/notes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("午后咖啡"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.commentCount").value(3))
                .andExpect(jsonPath("$.data.favoriteCount").value(2));
    }

    @Test
    void getNoteBusinessExceptionReturnsApiResponse() throws Exception {
        when(noteQueryService.getVisibleNote(404L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "note not found"));

        mockMvc.perform(get("/api/v1/notes/404"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value("note not found"));
    }

    @Test
    void pageMerchantNotesReturnsMerchantNoteCards() throws Exception {
        Page<NoteCardResponse> page = new Page<>(2, 8);
        page.setTotal(1);
        page.setRecords(List.of(noteCard(1L, "午后咖啡")));
        when(noteQueryService.pageMerchantNotes(1L, 2, 8)).thenReturn(page);

        mockMvc.perform(get("/api/v1/merchants/1/notes")
                        .param("pageNo", "2")
                        .param("pageSize", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(8))
                .andExpect(jsonPath("$.data.records[0].merchantId").value(1));

        verify(noteQueryService).pageMerchantNotes(1L, 2, 8);
    }

    @Test
    void pageMyNotesRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void pageMyNotesUsesCurrentUser() throws Exception {
        givenValidToken();
        Page<NoteCardResponse> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(noteCard(1L, "午后咖啡")));
        when(noteQueryService.pageCurrentUserNotes(10L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/users/me/notes")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(noteQueryService).pageCurrentUserNotes(10L, 1, 10);
    }

    @Test
    void createMyNoteRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "merchantId", 1,
                                "title", "New note",
                                "content", "Coffee is good",
                                "rating", 5))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void createMyNoteUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteCommandService.createCurrentUserNote(eq(10L), any()))
                .thenReturn(noteDetail(99L, "New note"));

        mockMvc.perform(post("/api/v1/users/me/notes")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "merchantId", 1,
                                "title", "New note",
                                "content", "Coffee is good",
                                "rating", 5,
                                "images", List.of("/assets/notes/new.jpg")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.title").value("New note"));

        verify(noteCommandService).createCurrentUserNote(eq(10L), any());
    }

    @Test
    void updateMyNoteRequiresLogin() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Updated note",
                                "content", "Coffee is still good",
                                "rating", 4))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void updateMyNoteUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteCommandService.updateCurrentUserNote(eq(10L), eq(99L), any()))
                .thenReturn(noteDetail(99L, "Updated note"));

        mockMvc.perform(put("/api/v1/users/me/notes/99")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Updated note",
                                "content", "Coffee is still good",
                                "rating", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.title").value("Updated note"));

        verify(noteCommandService).updateCurrentUserNote(eq(10L), eq(99L), any());
    }

    @Test
    void deleteMyNoteUsesCurrentUser() throws Exception {
        givenValidToken();

        mockMvc.perform(delete("/api/v1/users/me/notes/99")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(noteCommandService).deleteCurrentUserNote(10L, 99L);
    }

    @Test
    void pageNoteCommentsReturnsPublicComments() throws Exception {
        Page<NoteCommentResponse> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(noteComment(7L)));
        when(noteCommentService.pageNoteComments(1L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/notes/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(7))
                .andExpect(jsonPath("$.data.records[0].content").value("Same here"));
    }

    @Test
    void createNoteCommentRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/notes/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Same here"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void createNoteCommentUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteCommentService.createCurrentUserComment(eq(10L), eq(1L), any()))
                .thenReturn(noteComment(7L));

        mockMvc.perform(post("/api/v1/users/me/notes/1/comments")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Same here"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.userId").value(10));

        verify(noteCommentService).createCurrentUserComment(eq(10L), eq(1L), any());
    }

    @Test
    void createNoteCommentRejectsBlankContentBeforeServiceCall() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/v1/users/me/notes/1/comments")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.name()));

        verifyNoInteractions(noteCommentService);
    }

    @Test
    void deleteNoteCommentUsesCurrentUser() throws Exception {
        givenValidToken();

        mockMvc.perform(delete("/api/v1/users/me/note-comments/7")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(noteCommentService).deleteCurrentUserComment(10L, 7L);
    }

    @Test
    void deleteNoteCommentRequiresLogin() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/note-comments/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(noteCommentService);
    }

    @Test
    void pageFavoriteNotesRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/favorite-notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void pageFavoriteNotesUsesCurrentUser() throws Exception {
        givenValidToken();
        Page<NoteCardResponse> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(noteCard(4L, "Hotpot note")));
        when(noteFavoriteService.pageCurrentUserFavoriteNotes(10L, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/users/me/favorite-notes")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(4));

        verify(noteFavoriteService).pageCurrentUserFavoriteNotes(10L, 1, 10);
    }

    @Test
    void favoriteNoteUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteFavoriteService.favoriteCurrentUserNote(10L, 4L))
                .thenReturn(new NoteFavoriteResponse(4L, true));

        mockMvc.perform(post("/api/v1/users/me/notes/4/favorite")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.noteId").value(4))
                .andExpect(jsonPath("$.data.favorited").value(true));

        verify(noteFavoriteService).favoriteCurrentUserNote(10L, 4L);
    }

    @Test
    void favoriteNoteRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/notes/4/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(noteFavoriteService);
    }

    @Test
    void getNoteFavoriteRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/notes/4/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(noteFavoriteService);
    }

    @Test
    void getNoteFavoriteUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteFavoriteService.getCurrentUserFavorite(10L, 4L))
                .thenReturn(new NoteFavoriteResponse(4L, true));

        mockMvc.perform(get("/api/v1/users/me/notes/4/favorite")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.noteId").value(4))
                .andExpect(jsonPath("$.data.favorited").value(true));

        verify(noteFavoriteService).getCurrentUserFavorite(10L, 4L);
    }

    @Test
    void cancelNoteFavoriteRequiresLogin() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/notes/4/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

        verifyNoInteractions(noteFavoriteService);
    }

    @Test
    void cancelNoteFavoriteUsesCurrentUser() throws Exception {
        givenValidToken();
        when(noteFavoriteService.cancelCurrentUserFavorite(10L, 4L))
                .thenReturn(new NoteFavoriteResponse(4L, false));

        mockMvc.perform(delete("/api/v1/users/me/notes/4/favorite")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.favorited").value(false));

        verify(noteFavoriteService).cancelCurrentUserFavorite(10L, 4L);
    }

    @Test
    void pageMyVoucherOrdersRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/voucher-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void pageMyVoucherOrdersUsesCurrentUser() throws Exception {
        givenValidToken();
        Page<VoucherOrderSummaryResponse> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(orderSummary()));
        when(voucherOrderQueryService.pageCurrentUserOrders(10L, 1, 1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/users/me/voucher-orders")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("LSO202605220000000001"))
                .andExpect(jsonPath("$.data.records[0].voucherTitle").value("Coffee Flash Sale 19.9"));

        verify(voucherOrderQueryService).pageCurrentUserOrders(10L, 1, 1, 10);
    }

    @Test
    void getVoucherOrderRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/voucher-orders/LSO202605220000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void getVoucherOrderReturnsCurrentUserOrder() throws Exception {
        givenValidToken();
        when(voucherOrderQueryService.getCurrentUserOrder("LSO202605220000000001", 10L))
                .thenReturn(orderDetail());

        mockMvc.perform(get("/api/v1/voucher-orders/LSO202605220000000001")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("LSO202605220000000001"))
                .andExpect(jsonPath("$.data.merchantName").value("Moonlight Coffee"));

        verify(voucherOrderQueryService).getCurrentUserOrder("LSO202605220000000001", 10L);
    }

    @Test
    void getVoucherOrderBusinessExceptionReturnsApiResponse() throws Exception {
        givenValidToken();
        when(voucherOrderQueryService.getCurrentUserOrder("LSO202605220000000404", 10L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "order not found"));

        mockMvc.perform(get("/api/v1/voucher-orders/LSO202605220000000404")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value("order not found"));
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

    private static NoteCardResponse noteCard(Long id, String title) {
        return new NoteCardResponse(
                id,
                1L,
                "Moonlight Coffee",
                2001L,
                "Demo User 2001",
                null,
                title,
                "拿铁奶香很稳",
                5,
                List.of("/assets/merchants/coffee/moonlight-cover.jpg"),
                262,
                0,
                0,
                LocalDateTime.of(2026, 5, 20, 12, 0));
    }

    private static NoteDetailResponse noteDetail(Long id, String title) {
        return new NoteDetailResponse(
                id,
                1L,
                "Moonlight Coffee",
                10L,
                "demo",
                null,
                title,
                "Coffee is good",
                5,
                List.of("/assets/notes/new.jpg"),
                0,
                0,
                0,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                LocalDateTime.of(2026, 5, 20, 12, 0));
    }

    private static NoteCommentResponse noteComment(Long id) {
        return new NoteCommentResponse(
                id,
                1L,
                10L,
                "demo",
                null,
                null,
                "Same here",
                LocalDateTime.of(2026, 5, 20, 12, 30));
    }

    private static VoucherOrderSummaryResponse orderSummary() {
        return new VoucherOrderSummaryResponse(
                "LSO202605220000000001",
                1L,
                "Moonlight Coffee",
                List.of("/assets/merchants/coffee/moonlight-cover.jpg"),
                1001L,
                "Coffee Flash Sale 19.9",
                "Pay 19.9, save 20",
                1990L,
                1,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                null,
                null);
    }

    private static VoucherOrderDetailResponse orderDetail() {
        return new VoucherOrderDetailResponse(
                "LSO202605220000000001",
                1L,
                "Moonlight Coffee",
                "Chaoyang",
                "88 Lake Road",
                List.of("/assets/merchants/coffee/moonlight-cover.jpg"),
                1001L,
                "Coffee Flash Sale 19.9",
                "Pay 19.9, save 20",
                "Weekday only",
                1990L,
                2000L,
                2,
                1,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                null,
                null);
    }
}
