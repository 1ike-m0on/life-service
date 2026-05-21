package io.github.ikemoon.lifeservice.user.controller;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import io.github.ikemoon.lifeservice.common.security.BearerTokenResolver;
import io.github.ikemoon.lifeservice.user.service.AuthResponse;
import io.github.ikemoon.lifeservice.user.service.CurrentUserResponse;
import io.github.ikemoon.lifeservice.user.service.EmailLoginRequest;
import io.github.ikemoon.lifeservice.user.service.UserAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserAuthService userAuthService;

    public AuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        return ApiResponse.ok(userAuthService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(userAuthService.currentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userAuthService.logout(BearerTokenResolver.resolve(authorization));
        return ApiResponse.ok();
    }
}
