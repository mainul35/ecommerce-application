package com.ecommerce.admin.controller;

import com.ecommerce.dto.ManagerCreateRequest;
import com.ecommerce.dto.UserDto;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only manager management. Authorization is enforced by an explicit
 * ADMIN rule in {@link com.ecommerce.security.AccessRules} that runs BEFORE
 * the broader admin-staff rule, so MANAGER accounts cannot reach these
 * endpoints even though they can otherwise navigate the admin console.
 */
@RestController
@RequestMapping("/api/admin/managers")
@RequiredArgsConstructor
@Tag(name = "Admin - Managers", description = "Provision and block manager accounts")
public class AdminManagerController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all manager accounts (active + blocked)")
    public Flux<UserDto> list() {
        return userService.listManagers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new manager account")
    public Mono<UserDto> create(@Valid @RequestBody ManagerCreateRequest request) {
        return userService.createManager(request);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Block or unblock a manager (toggles isActive)")
    public Mono<UserDto> setActive(@PathVariable UUID id,
                                    @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return userService.setManagerActive(id, active);
    }
}
