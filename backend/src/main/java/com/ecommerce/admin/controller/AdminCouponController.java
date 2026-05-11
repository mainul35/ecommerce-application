package com.ecommerce.admin.controller;

import com.ecommerce.dto.CouponDto;
import com.ecommerce.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin coupon CRUD. Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules}.
 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Admin - Coupons", description = "Coupon code management")
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    @Operation(summary = "List all coupons (newest first)")
    public Flux<CouponDto> list() {
        return couponService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a coupon by ID")
    public Mono<CouponDto> get(@PathVariable UUID id) {
        return couponService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a coupon")
    public Mono<CouponDto> create(@Valid @RequestBody CouponDto dto) {
        return couponService.create(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a coupon")
    public Mono<CouponDto> update(@PathVariable UUID id, @Valid @RequestBody CouponDto dto) {
        return couponService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a coupon permanently")
    public Mono<Void> delete(@PathVariable UUID id) {
        return couponService.delete(id);
    }
}
