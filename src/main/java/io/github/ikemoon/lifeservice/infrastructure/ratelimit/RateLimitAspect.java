package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final RateLimitKeyResolver keyResolver;

    public RateLimitAspect(SlidingWindowRateLimiter slidingWindowRateLimiter, RateLimitKeyResolver keyResolver) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.keyResolver = keyResolver;
    }

    @Around("@annotation(io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiter)"
            + " || @annotation(io.github.ikemoon.lifeservice.infrastructure.ratelimit.RateLimiters)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Class<?> targetClass = resolveTargetClass(joinPoint);
        RateLimiter[] rateLimiters = method.getAnnotationsByType(RateLimiter.class);

        for (RateLimiter rateLimiter : rateLimiters) {
            checkRateLimit(rateLimiter, method, targetClass);
        }

        return joinPoint.proceed();
    }

    private void checkRateLimit(RateLimiter rateLimiter, Method method, Class<?> targetClass) {
        String key = keyResolver.resolveKey(rateLimiter, method, targetClass);
        try {
            if (!slidingWindowRateLimiter.isAllowed(key, rateLimiter.window(), rateLimiter.limit())) {
                throw new RateLimitException(rateLimiter.message());
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Rate limit check failed, key={}, failureStrategy={}", key, rateLimiter.failureStrategy(), e);
            if (rateLimiter.failureStrategy() == RateLimitFailureStrategy.FAIL_CLOSED) {
                throw new RateLimitException(rateLimiter.message());
            }
        }
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
