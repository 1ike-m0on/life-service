package io.github.ikemoon.lifeservice.order.enums;

public enum StockReleaseTaskStatus {
    PENDING(1),
    SUCCESS(2),
    FAILED(3);

    private final int code;

    StockReleaseTaskStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
