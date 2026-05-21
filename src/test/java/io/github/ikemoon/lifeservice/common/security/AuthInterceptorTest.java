package io.github.ikemoon.lifeservice.common.security;

import io.github.ikemoon.lifeservice.common.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private AuthTokenService authTokenService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void preHandleRequiresBearerToken() {
        AuthInterceptor interceptor = new AuthInterceptor(authTokenService);

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> interceptor.preHandle(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        new Object()));
    }

    @Test
    void preHandleSetsUserContextWhenTokenIsValid() {
        AuthInterceptor interceptor = new AuthInterceptor(authTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc");
        when(authTokenService.resolve("abc"))
                .thenReturn(Optional.of(new UserPrincipal(10L, "demo@life.local", "demo")));

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        assertThat(UserContext.get()).isEqualTo(new UserPrincipal(10L, "demo@life.local", "demo"));
    }

    @Test
    void preHandleRejectsExpiredToken() {
        AuthInterceptor interceptor = new AuthInterceptor(authTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired");
        when(authTokenService.resolve("expired")).thenReturn(Optional.empty());

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void afterCompletionClearsUserContext() {
        AuthInterceptor interceptor = new AuthInterceptor(authTokenService);
        UserContext.set(new UserPrincipal(10L, "demo"));

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(UserContext.get()).isNull();
    }
}
