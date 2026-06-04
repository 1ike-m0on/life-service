package io.github.ikemoon.lifeservice.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public AuthConfiguration(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/me",
                        "/api/v1/auth/logout",
                        "/api/v1/flash-sale-vouchers/*/orders",
                        "/api/v1/users/me/notes",
                        "/api/v1/users/me/notes/*",
                        "/api/v1/users/me/notes/*/comments",
                        "/api/v1/users/me/notes/*/favorite",
                        "/api/v1/users/me/note-comments/*",
                        "/api/v1/users/me/favorite-notes",
                        "/api/v1/users/me/voucher-orders",
                        "/api/v1/voucher-orders/*",
                        "/api/v1/voucher-orders/*/payment");
    }
}
