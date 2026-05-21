package io.github.ikemoon.lifeservice.common.security;

import io.github.ikemoon.lifeservice.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class AuthTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations hashOperations;

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setTokenPrefix("life:auth:test:");
        authProperties.setTokenTtl(Duration.ofMinutes(30));
        authTokenService = new AuthTokenService(redisTemplate, authProperties);
    }

    @Test
    void issueTokenStoresUserHashAndTtl() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        User user = new User();
        user.setId(10L);
        user.setEmail("demo@life.local");
        user.setNickname("demo");

        String token = authTokenService.issueToken(user);

        assertThat(token).hasSize(64);
        verify(hashOperations).putAll(eq("life:auth:test:" + token), anyMap());
        verify(redisTemplate).expire("life:auth:test:" + token, Duration.ofMinutes(30).toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void resolveReturnsPrincipalAndRefreshesTtl() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("life:auth:test:abc")).thenReturn(Map.of(
                "userId", "10",
                "email", "demo@life.local",
                "nickname", "demo"));

        Optional<UserPrincipal> principal = authTokenService.resolve("abc");

        assertThat(principal).contains(new UserPrincipal(10L, "demo@life.local", "demo"));
        verify(redisTemplate).expire("life:auth:test:abc", Duration.ofMinutes(30).toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void resolveReturnsEmptyWhenRedisHashMissing() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("life:auth:test:missing")).thenReturn(Map.of());

        Optional<UserPrincipal> principal = authTokenService.resolve("missing");

        assertThat(principal).isEmpty();
    }

    @Test
    void revokeDeletesTokenKey() {
        authTokenService.revoke("abc");

        verify(redisTemplate).delete("life:auth:test:abc");
    }
}
