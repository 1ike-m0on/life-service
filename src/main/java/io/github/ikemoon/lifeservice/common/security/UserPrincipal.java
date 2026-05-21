package io.github.ikemoon.lifeservice.common.security;

public record UserPrincipal(Long id, String email, String nickname) {

    public UserPrincipal(Long id, String nickname) {
        this(id, null, nickname);
    }
}
