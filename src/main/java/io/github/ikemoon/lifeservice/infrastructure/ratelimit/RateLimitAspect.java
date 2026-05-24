package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.exception.RateLimitException;
import io.github.ikemoon.lifeservice.infrastructure.metrics.RateLimitMetrics;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@ConditionalOnProperty(name = "life-service.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitMetrics rateLimitMetrics;
    @Value("${life-service.rate-limit.ip-enabled:true}")
    private boolean ipEnabled = true;
    @Value("${life-service.rate-limit.global-limit-override:0}")
    private int globalLimitOverride;

    public RateLimitAspect(
            SlidingWindowRateLimiter slidingWindowRateLimiter,
            RateLimitKeyResolver keyResolver,
            RateLimitMetrics rateLimitMetrics) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.keyResolver = keyResolver;
        this.rateLimitMetrics = rateLimitMetrics;
    }

    @Around("@annotation(io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter)"
            + " || @annotation(io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiters)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Class<?> targetClass = resolveTargetClass(joinPoint);
        RateLimiter[] rateLimiters = method.getAnnotationsByType(RateLimiter.class);

        for (RateLimiter rateLimiter : rateLimiters) {
            if (shouldSkip(rateLimiter)) {
                continue;
            }
            checkRateLimit(rateLimiter, method, targetClass);
        }

        return joinPoint.proceed();
    }

    private boolean shouldSkip(RateLimiter rateLimiter) {
        return rateLimiter.type() == RateLimitType.IP && !ipEnabled;
    }

    private void checkRateLimit(RateLimiter rateLimiter, Method method, Class<?> targetClass) {
        String key = keyResolver.resolveKey(rateLimiter, method, targetClass);
        String endpointGroup = targetClass.getSimpleName() + "." + method.getName();
        int limit = resolveLimit(rateLimiter);
        try {
            if (!slidingWindowRateLimiter.isAllowed(key, rateLimiter.window(), limit)) {
                rateLimitMetrics.recordRejected(rateLimiter.key(), rateLimiter.type(), endpointGroup);
                throw new RateLimitException(rateLimiter.message());
            }
            rateLimitMetrics.recordAllowed(rateLimiter.key(), rateLimiter.type(), endpointGroup);
        } catch (RateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Rate limit check failed, key={}, failureStrategy={}", key, rateLimiter.failureStrategy(), e);
            if (rateLimiter.failureStrategy() == RateLimitFailureStrategy.FAIL_CLOSED) {
                rateLimitMetrics.recordRejected(rateLimiter.key(), rateLimiter.type(), endpointGroup);
                throw new RateLimitException(rateLimiter.message());
            }
            rateLimitMetrics.recordAllowed(rateLimiter.key(), rateLimiter.type(), endpointGroup);
        }
    }

    private int resolveLimit(RateLimiter rateLimiter) {
        if (rateLimiter.type() == RateLimitType.GLOBAL && globalLimitOverride > 0) {
            return globalLimitOverride;
        }
        return rateLimiter.limit();
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return AopUtils.getMostSpecificMethod(method, resolveTargetClass(joinPoint));
    }

    private static Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target == null) {
            return ((MethodSignature) joinPoint.getSignature()).getDeclaringType();
        }
        return AopUtils.getTargetClass(target);
    }
}
