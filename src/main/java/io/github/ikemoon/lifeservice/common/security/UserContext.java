package io.github.ikemoon.lifeservice.common.security;

import io.github.ikemoon.lifeservice.common.exception.AuthException;

public final class UserContext {

    private static final ThreadLocal<UserPrincipal> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal user) {
        CURRENT.set(user);
    }

    public static UserPrincipal get() {
        return CURRENT.get();
    }

    public static Long userIdOrNull() {
        UserPrincipal user = CURRENT.get();
        return user == null ? null : user.id();
    }

    public static UserPrincipal require() {
        UserPrincipal user = CURRENT.get();
        if (user == null) {
            throw new AuthException("请先登录");
        }
        return user;
    }

    public static Long requiredUserId() {
        return require().id();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
