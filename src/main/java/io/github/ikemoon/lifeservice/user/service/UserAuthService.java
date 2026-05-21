package io.github.ikemoon.lifeservice.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.ikemoon.lifeservice.common.exception.BusinessException;
import io.github.ikemoon.lifeservice.common.exception.ErrorCode;
import io.github.ikemoon.lifeservice.common.security.AuthTokenService;
import io.github.ikemoon.lifeservice.common.security.UserContext;
import io.github.ikemoon.lifeservice.common.security.UserPrincipal;
import io.github.ikemoon.lifeservice.user.entity.User;
import io.github.ikemoon.lifeservice.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserAuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String NICKNAME_PREFIX = "user_";

    private final UserMapper userMapper;
    private final AuthTokenService authTokenService;

    public UserAuthService(UserMapper userMapper, AuthTokenService authTokenService) {
        this.userMapper = userMapper;
        this.authTokenService = authTokenService;
    }

    @Transactional
    public AuthResponse login(EmailLoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = findByEmail(email);
        if (user == null) {
            user = createUser(email);
        }
        String token = authTokenService.issueToken(user);
        return new AuthResponse(token, TOKEN_TYPE, user.getId(), user.getEmail(), user.getNickname());
    }

    public CurrentUserResponse currentUser() {
        UserPrincipal user = UserContext.require();
        return new CurrentUserResponse(user.id(), user.email(), user.nickname());
    }

    public void logout(String token) {
        authTokenService.revoke(token);
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setNickname(defaultNickname(email));
        user.setStatus(1);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException ex) {
            User existing = findByEmail(email);
            if (existing == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，请稍后再试", ex);
            }
            return existing;
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultNickname(String email) {
        String name = email.substring(0, email.indexOf('@'));
        String normalized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (normalized.isBlank()) {
            normalized = "email";
        }
        return NICKNAME_PREFIX + normalized;
    }
}
