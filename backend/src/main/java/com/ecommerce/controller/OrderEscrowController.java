package com.ecommerce.controller;

import com.ecommerce.dto.EscrowTransactionDto;
import com.ecommerce.dto.ReturnCreateRequest;
import com.ecommerce.dto.ReturnRequestDto;
import com.ecommerce.service.EscrowService;
import com.ecommerce.service.ReturnService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Buyer-facing escrow surface of an order: see where the held money stands,
 * confirm receipt to release it early, and request per-item returns.
 */
@RestController
@RequestMapping("/api/orders/{orderId}")
@RequiredArgsConstructor
@Tag(name = "Order escrow", description = "Buyer protection: escrow status, receipt confirmation, returns")
public class OrderEscrowController {

    private final EscrowService escrowService;
    private final ReturnService returnService;
    private final UserService userService;

    @GetMapping("/escrow")
    @Operation(summary = "Escrow status of the order, one entry per seller group")
    public Flux<EscrowTransactionDto> getEscrow(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID orderId) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMapMany(user -> escrowService.findByOrderForBuyer(orderId, user.getId()));
    }

    @PostMapping("/confirm-receipt")
    @Operation(summary = "Confirm receipt: releases all held (non-disputed) funds to the sellers")
    public Mono<List<EscrowTransactionDto>> confirmReceipt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID orderId) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> escrowService.confirmReceipt(orderId, user.getId()));
    }

    @PostMapping("/items/{itemId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a (partial) return of one delivered item")
    public Mono<ReturnRequestDto> requestReturn(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReturnCreateRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> returnService.request(user.getId(), orderId, itemId,
                        request.getQuantity(), request.getReason()));
    }
}
