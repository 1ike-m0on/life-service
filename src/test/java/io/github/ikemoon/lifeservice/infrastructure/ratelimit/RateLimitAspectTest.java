package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.exception.RateLimitException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

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

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect(slidingWindowRateLimiter, keyResolver);
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
    }

    private void prepareJoinPoint(Method method) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(methodSignature.getMethod()).thenReturn(method);
    }

    static class SampleController {

        @RateLimiter(key = "life:rate:test:", window = 1, limit = 2)
        @RateLimiter(key = "life:rate:test:", window = 10, limit = 3)
        String multiple() {
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
