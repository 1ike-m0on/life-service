package io.github.ikemoon.lifeservice.infrastructure.cache;

public final class CacheConstants {

    public static final String MERCHANT_KEY_PREFIX = "life:cache:merchant:";
    public static final String MERCHANT_REBUILD_LOCK_PREFIX = "life:lock:cache:merchant:";
    public static final String MERCHANT_CATEGORY_LIST_KEY = "life:cache:merchant-category:list";
    public static final String FLASH_SALE_VOUCHER_KEY_PREFIX = "life:cache:flash-sale-voucher:";
    public static final String FLASH_SALE_STOCK_KEY_PREFIX = "life:flash:voucher:stock:";
    public static final String FLASH_SALE_USERS_KEY_PREFIX = "life:flash:voucher:users:";
    public static final String FLASH_SALE_RELEASED_ORDER_KEY_PREFIX = "life:flash:voucher:released-orders:";
    public static final String FLASH_SALE_USERS_READY_MARKER = "__READY__";

    private CacheConstants() {
    }
}
