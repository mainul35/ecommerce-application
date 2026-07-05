package com.ecommerce.admin.controller;

import com.ecommerce.dto.auth.AuthResponse;
import com.ecommerce.dto.auth.LoginRequest;
import com.ecommerce.dto.auth.RefreshRequest;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Dedicated admin login. Lives outside the JWT-required path matchers in
 * {@link com.ecommerce.security.AccessRules} (permitAll on /api/admin/auth/**)
 * but the underlying {@link UserService#adminLogin} rejects any non-ADMIN
 * account, so customers cannot sign in here.
 *
 * Customer login at /api/auth/login symmetrically rejects ADMIN accounts.
 * The two surfaces are completely separated.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin - Authentication", description = "Admin login")
public class AdminAuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Admin login (rejects non-admin accounts)")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return userService.adminLogin(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange an admin refresh token for a new access token")
    public Mono<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return userService.refreshAdmin(request.getRefreshToken());
    }
}
