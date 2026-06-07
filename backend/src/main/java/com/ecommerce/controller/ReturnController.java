package com.ecommerce.controller;

import com.ecommerce.dto.ReturnRequestDto;
import com.ecommerce.service.ReturnService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Buyer view of their return requests. Creation lives on the order surface
 * (POST /api/orders/{orderId}/items/{itemId}/returns).
 */
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@Tag(name = "Returns", description = "Per-item return requests")
public class ReturnController {

    private final ReturnService returnService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "My return requests, newest first")
    public Flux<ReturnRequestDto> mine(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMapMany(user -> returnService.findMine(user.getId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Withdraw a pending return request")
    public Mono<ReturnRequestDto> cancel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> returnService.cancel(user.getId(), id));
    }
}
