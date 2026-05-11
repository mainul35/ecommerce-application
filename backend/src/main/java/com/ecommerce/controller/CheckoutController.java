package com.ecommerce.controller;

import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.StripeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing checkout endpoint. Creates a Stripe Checkout Session for an
 * existing PENDING order and returns the hosted-checkout URL the frontend should
 * redirect to.
 *
 * Authorization is enforced by AccessRules (CUSTOMER role required for /api/orders/**).
 * The handler additionally checks that the order belongs to the JWT user.
 */
@RestController
@RequestMapping("/api/orders/{orderId}/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Payment checkout (Stripe)")
public class CheckoutController {

    private final OrderRepository orderRepository;
    private final StripeService stripeService;

    @PostMapping("/session")
    @Operation(summary = "Create a Stripe Checkout Session for the order")
    public Mono<Map<String, String>> createSession(@PathVariable UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMap(stripeService::createCheckoutSession)
                .map(url -> Map.of("checkoutUrl", url));
    }
}
