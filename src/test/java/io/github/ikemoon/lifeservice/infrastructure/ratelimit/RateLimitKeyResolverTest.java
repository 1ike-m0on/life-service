package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {

    private final RateLimitKeyResolver resolver = new RateLimitKeyResolver();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        UserContext.clear();
    }

    @Test
    void resolvesGlobalKey() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("global");

        String key = resolver.resolveKey(method.getAnnotation(RateLimiter.class), method, SampleController.class);

        assertThat(key).isEqualTo("life:rate:test:SampleController:global");
    }

    @Test
    void resolvesIpKeyFromForwardedForHeader() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("ip");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.1.10, 10.0.0.2");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String key = resolver.resolveKey(method.getAnnotation(RateLimiter.class), method, SampleController.class);

        assertThat(key).isEqualTo("life:rate:test:SampleController:ip:ip:192.168.1.10");
    }

    @Test
    void resolvesUserKeyFromUserContext() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("user");
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UserContext.set(new UserPrincipal(3001L, "tester"));

        String key = resolver.resolveKey(method.getAnnotation(RateLimiter.class), method, SampleController.class);

        assertThat(key).isEqualTo("life:rate:test:SampleController:user:user:3001");
    }

    @Test
    void resolvesAnonymousUserWhenNoUserIsAvailable() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("user");

        String key = resolver.resolveKey(method.getAnnotation(RateLimiter.class), method, SampleController.class);

        assertThat(key).isEqualTo("life:rate:test:SampleController:user:user:anonymous");
    }

    static class SampleController {

        @RateLimiter(key = "life:rate:test:", type = RateLimitType.GLOBAL)
        void global() {
        }

        @RateLimiter(key = "life:rate:test:", type = RateLimitType.IP)
        void ip() {
        }

        @RateLimiter(key = "life:rate:test:", type = RateLimitType.USER)
        void user() {
        }
    }
}
