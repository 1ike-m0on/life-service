package io.github.ikemoon.lifeservice.common.security;

import io.github.ikemoon.lifeservice.common.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final AuthTokenService authTokenService;

    public AuthInterceptor(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = BearerTokenResolver.resolve(request.getHeader(AUTHORIZATION_HEADER));
        if (token == null) {
            throw new AuthException("请先登录");
        }
        UserPrincipal principal = authTokenService.resolve(token)
                .orElseThrow(() -> new AuthException("登录状态已失效，请重新登录"));
        UserContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        UserContext.clear();
    }
}
