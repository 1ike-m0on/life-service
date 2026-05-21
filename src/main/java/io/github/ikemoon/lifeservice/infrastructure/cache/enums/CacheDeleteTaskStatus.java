package io.github.ikemoon.lifeservice.infrastructure.cache.enums;

public enum CacheDeleteTaskStatus {
    PENDING(1),
    SUCCESS(2),
    FAILED(3);

    private final int code;

    CacheDeleteTaskStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
