package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimiters.class)
public @interface RateLimiter {

    String key() default "life:rate:";

    int window() default 10;

    int limit() default 20;

    String message() default "Too many requests, please try again later";

    RateLimitType type() default RateLimitType.GLOBAL;

    RateLimitFailureStrategy failureStrategy() default RateLimitFailureStrategy.FAIL_CLOSED;
}
