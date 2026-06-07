package com.ecommerce.controller;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.payment.PaymentGatewayRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing checkout endpoint. Routes to the appropriate PaymentGateway
 * implementation based on the caller-supplied gateway ID.
 */
@RestController
@RequestMapping("/api/orders/{orderId}/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Payment checkout")
public class CheckoutController {

    private final OrderRepository orderRepository;
    private final PaymentGatewayRegistry gatewayRegistry;

    @PostMapping("/session")
    @Operation(summary = "Create a checkout session for the order via the chosen gateway")
    public Mono<Map<String, String>> createSession(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "stripe") String gateway) {

        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                // Persist the chosen gateway so refunds can route back through it later.
                .flatMap(order -> {
                    order.setPaymentMethod(gateway);
                    return orderRepository.save(order);
                })
                .flatMap(order -> gatewayRegistry.find(gateway)
                        .map(gw -> gw.createCheckoutSession(order))
                        .orElseGet(() -> Mono.error(
                                new BadRequestException("Unknown payment gateway: " + gateway))))
                .map(url -> Map.of("checkoutUrl", url));
    }
}
