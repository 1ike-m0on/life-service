package io.github.ikemoon.lifeservice.order.enums;

public enum OrderStatus {
    PENDING_PAYMENT(1),
    PAID(2),
    CLOSED(3);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
