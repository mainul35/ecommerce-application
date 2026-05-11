package com.ecommerce.controller;

import com.ecommerce.dto.CouponValidationRequest;
import com.ecommerce.dto.CouponValidationResponse;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.service.CouponService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Customer-side coupon validation. The cart UI calls this to preview the
 * effect of typing a code; the actual redemption is recorded server-side
 * during order creation (re-validates the same rules).
 *
 * Authorization: gated to CUSTOMER role by AccessRules' /api/coupons/** rule.
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Customer coupon validation")
public class CouponController {

    private final CouponService couponService;
    private final UserService userService;

    @PostMapping("/validate")
    @Operation(summary = "Preview the effect of a coupon code on the given cart items")
    public Mono<CouponValidationResponse> validate(@Valid @RequestBody CouponValidationRequest request) {
        return currentUserId()
                .flatMap(userId -> couponService.previewForCustomer(userId, request));
    }

    private Mono<UUID> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userService::findByEmail)
                .map(u -> u.getId())
                .switchIfEmpty(Mono.error(new UnauthorizedException("Authentication required")));
    }
}
