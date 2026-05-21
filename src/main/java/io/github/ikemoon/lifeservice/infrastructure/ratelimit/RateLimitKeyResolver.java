package io.github.ikemoon.lifeservice.infrastructure.ratelimit;

import io.github.ikemoon.lifeservice.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Component
public class RateLimitKeyResolver {

    private static final int MAX_DIMENSION_VALUE_LENGTH = 128;

    public String resolveKey(RateLimiter rateLimiter, Method method, Class<?> targetClass) {
        StringBuilder key = new StringBuilder(normalizePrefix(rateLimiter.key()))
                .append(targetClass.getSimpleName())
                .append(':')
                .append(method.getName());

        if (rateLimiter.type() == RateLimitType.IP) {
            key.append(":ip:").append(normalizeDimensionValue(resolveClientIp()));
        } else if (rateLimiter.type() == RateLimitType.USER) {
            key.append(":user:").append(normalizeDimensionValue(resolveUserId()));
        }

        return key.toString();
    }

    private static String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "life:rate:";
        }
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private static String resolveUserId() {
        Long userId = UserContext.userIdOrNull();
        if (userId != null) {
            return userId.toString();
        }
        return "anonymous";
    }

    private static String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String ip = firstNonBlank(
                firstForwardedForIp(request.getHeader("X-Forwarded-For")),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"),
                request.getRemoteAddr());
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private static String firstForwardedForIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        return forwardedFor.split(",")[0].trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalizeDimensionValue(String value) {
        String normalized = value == null ? "unknown" : value.trim();
        if (normalized.length() > MAX_DIMENSION_VALUE_LENGTH) {
            normalized = normalized.substring(0, MAX_DIMENSION_VALUE_LENGTH);
        }
        return normalized.replaceAll("[^a-zA-Z0-9.:-]", "_");
    }
}
