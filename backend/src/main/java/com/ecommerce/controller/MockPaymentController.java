package com.ecommerce.controller;

import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Backs the frontend MockPaymentPage. Simulates payment confirmation or
 * cancellation without going through a real payment provider.
 *
 * This endpoint is intentionally public so it can be reached from the
 * frontend mock page without requiring a session token in the URL.
 * It verifies the order exists and is in PENDING state before confirming.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/mock")
@RequiredArgsConstructor
@Tag(name = "Mock Payment", description = "Development-only payment simulation")
public class MockPaymentController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Simulate a successful payment (dev only)")
    public Mono<Map<String, String>> confirm(@PathVariable UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMap(order -> {
                    if (order.getPaymentStatus() != null &&
                            order.getPaymentStatus().name().equals("COMPLETED")) {
                        return Mono.just(order);
                    }
                    return orderService.markPaid(orderId, "mock:" + orderId);
                })
                .map(order -> {
                    String url = successUrl.replace("{CHECKOUT_SESSION_ID}", orderId.toString());
                    log.info("Mock payment confirmed for order {}", orderId);
                    return Map.of("redirectUrl", url);
                });
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Simulate a cancelled payment (dev only)")
    public Mono<Map<String, String>> cancel(@PathVariable UUID orderId) {
        String url = cancelUrl.replace("{CHECKOUT_SESSION_ID}", orderId.toString());
        log.info("Mock payment cancelled for order {}", orderId);
        return Mono.just(Map.of("redirectUrl", url));
    }
}
