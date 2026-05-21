package io.github.ikemoon.lifeservice.common.security;

import io.github.ikemoon.lifeservice.user.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AuthTokenService {

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_NICKNAME = "nickname";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
    }

    public String issueToken(User user) {
        String token = newToken();
        redisTemplate.opsForHash().putAll(tokenKey(token), Map.of(
                FIELD_USER_ID, user.getId().toString(),
                FIELD_EMAIL, user.getEmail(),
                FIELD_NICKNAME, user.getNickname()));
        redisTemplate.expire(tokenKey(token), authProperties.getTokenTtl().toMillis(), TimeUnit.MILLISECONDS);
        return token;
    }

    public Optional<UserPrincipal> resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(tokenKey(token));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        String userId = stringValue(entries.get(FIELD_USER_ID));
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId == null) {
            return Optional.empty();
        }
        redisTemplate.expire(tokenKey(token), authProperties.getTokenTtl().toMillis(), TimeUnit.MILLISECONDS);
        return Optional.of(new UserPrincipal(
                parsedUserId,
                stringValue(entries.get(FIELD_EMAIL)),
                stringValue(entries.get(FIELD_NICKNAME))));
    }

    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(tokenKey(token));
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String tokenKey(String token) {
        return authProperties.getTokenPrefix() + token;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
