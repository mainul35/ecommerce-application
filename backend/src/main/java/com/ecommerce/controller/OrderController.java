package com.ecommerce.controller;

import com.ecommerce.model.User;
import com.ecommerce.dto.OrderCreateRequest;
import com.ecommerce.dto.OrderDto;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management operations")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get current user's orders")
    public Mono<PagedResponse<OrderDto>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> orderService.findByUserId(user.getId(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public Mono<OrderDto> getOrderById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> orderService.findById(id, user.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new order")
    public Mono<OrderDto> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OrderCreateRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> orderService.create(user.getId(), request));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    public Mono<OrderDto> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> orderService.cancel(id, user.getId()));
    }
}
