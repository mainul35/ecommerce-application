package com.ecommerce.admin.controller;

import com.ecommerce.dto.AdminProfileUpdateRequest;
import com.ecommerce.dto.UserDto;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Lets the currently authenticated admin view and update their own profile
 * (username/email, first/last name, password). Authorization is enforced
 * by path matching in {@link com.ecommerce.security.AccessRules}; the
 * controller resolves "me" from the security context, so an admin can only
 * change their own credentials - never another user's.
 */
@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
@Tag(name = "Admin - Profile", description = "Current admin profile/credentials")
public class AdminMeController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get the currently authenticated admin's profile")
    public Mono<UserDto> getCurrent() {
        return currentUserEmail().flatMap(userService::findByEmail);
    }

    @PutMapping
    @Operation(summary = "Update the current admin's profile/credentials")
    public Mono<UserDto> updateCurrent(@Valid @RequestBody AdminProfileUpdateRequest request) {
        return currentUserEmail().flatMap(email -> userService.updateProfile(email, request));
    }

    private Mono<String> currentUserEmail() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName());
    }
}
