package io.github.ikemoon.lifeservice.user.service;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String email,
        String nickname) {
}
