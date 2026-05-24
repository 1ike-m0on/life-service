package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.exception.RateLimitException;
import io.github.ikemoon.lifeservice.infrastructure.metrics.RateLimitMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Mock
    private RateLimitKeyResolver keyResolver;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private RateLimitAspect aspect;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new RateLimitAspect(slidingWindowRateLimiter, keyResolver, new RateLimitMetrics(meterRegistry));
    }

    @Test
    void rateLimitProceedsWhenAllLimitsAllow() throws Throwable {
        Method method = SampleController.class.getDeclaredMethod("multiple");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 2)).thenReturn(true);
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 10, 3)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(slidingWindowRateLimiter, times(2)).isAllowed(eq("life:rate:test"), anyInt(), anyInt());
        assertThat(rateLimitCounter("life.rate.limit.allowed", "SampleController.multiple")).isEqualTo(2);
    }

    @Test
    void rateLimitThrowsWhenLimitIsExceeded() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("closed");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 1)).thenReturn(false);

        assertThatExceptionOfType(RateLimitException.class)
                .isThrownBy(() -> aspect.rateLimit(joinPoint))
                .withMessage("closed");
        assertThat(rateLimitCounter("life.rate.limit.rejected", "SampleController.closed")).isEqualTo(1);
    }

    @Test
    void rateLimitFailsClosedWhenRedisFails() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("closed");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 1))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatExceptionOfType(RateLimitException.class)
                .isThrownBy(() -> aspect.rateLimit(joinPoint))
                .withMessage("closed");
        assertThat(rateLimitCounter("life.rate.limit.rejected", "SampleController.closed")).isEqualTo(1);
    }

    @Test
    void rateLimitFailsOpenWhenRedisFails() throws Throwable {
        Method method = SampleController.class.getDeclaredMethod("open");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 1))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(rateLimitCounter("life.rate.limit.allowed", "SampleController.open")).isEqualTo(1);
    }

    @Test
    void rateLimitSkipsIpRuleWhenIpLimitIsDisabled() throws Throwable {
        ReflectionTestUtils.setField(aspect, "ipEnabled", false);
        Method method = SampleController.class.getDeclaredMethod("globalAndIp");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 2)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(slidingWindowRateLimiter, times(1)).isAllowed("life:rate:test", 1, 2);
        assertThat(rateLimitCounter("life.rate.limit.allowed", "SampleController.globalAndIp")).isEqualTo(1);
    }

    @Test
    void rateLimitUsesGlobalLimitOverrideWhenConfigured() throws Throwable {
        ReflectionTestUtils.setField(aspect, "globalLimitOverride", 5000);
        Method method = SampleController.class.getDeclaredMethod("globalOnly");
        prepareJoinPoint(method);
        when(keyResolver.resolveKey(any(RateLimiter.class), eq(method), eq(SampleController.class)))
                .thenReturn("life:rate:test");
        when(slidingWindowRateLimiter.isAllowed("life:rate:test", 1, 5000)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(slidingWindowRateLimiter).isAllowed("life:rate:test", 1, 5000);
        assertThat(rateLimitCounter("life.rate.limit.allowed", "SampleController.globalOnly")).isEqualTo(1);
    }

    private void prepareJoinPoint(Method method) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(methodSignature.getMethod()).thenReturn(method);
    }

    private double rateLimitCounter(String name, String endpointGroup) {
        return meterRegistry.counter(
                name,
                "rule", "test",
                "scope", "global",
                "endpoint_group", endpointGroup)
                .count();
    }

    static class SampleController {

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 2)
        @RateLimiter(key = "life:rate:test:", window = 10, limit = 3)
        String multiple() {
            return "ok";
        }

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 2)
        @RateLimiter(key = "life:rate:test:", window = 10, limit = 3, type = RateLimitType.IP)
        String globalAndIp() {
            return "ok";
        }

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 1000, type = RateLimitType.GLOBAL)
        String globalOnly() {
            return "ok";
        }

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 1, message = "closed",
                failureStrategy = RateLimitFailureStrategy.FAIL_CLOSED)
        String closed() {
            return "ok";
        }

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 1, message = "open",
                failureStrategy = RateLimitFailureStrategy.FAIL_OPEN)
        String open() {
            return "ok";
        }
    }
}
