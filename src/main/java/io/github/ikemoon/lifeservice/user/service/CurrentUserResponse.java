package io.github.ikemoon.lifeservice.user.service;

public record CurrentUserResponse(Long userId, String email, String nickname) {
}
