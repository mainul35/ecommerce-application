package com.ecommerce.controller;

import com.ecommerce.service.OrderService;
import com.ecommerce.service.StripeService;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Receives webhook events from Stripe. The endpoint is publicly reachable
 * (whitelisted in {@link com.ecommerce.security.AccessRules}) but every request
 * is authenticated by HMAC signature against the configured webhook secret.
 *
 * On {@code checkout.session.completed} we look up the order via session metadata
 * and call {@link OrderService#markPaid} - which commits reservations into a
 * permanent stock decrement and flips the order to CONFIRMED.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe webhooks", description = "Stripe event receiver")
public class StripeWebhookController {

    private static final String CHECKOUT_COMPLETED = "checkout.session.completed";

    private final StripeService stripeService;
    private final OrderService orderService;

    @PostMapping
    public Mono<ResponseEntity<String>> handle(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {

        return stripeService.parseWebhookEvent(payload, signature)
                .flatMap(event -> {
                    if (!CHECKOUT_COMPLETED.equals(event.getType())) {
                        log.debug("Ignoring Stripe event type: {}", event.getType());
                        return Mono.just(ResponseEntity.ok("ignored"));
                    }
                    Session session = (Session) event.getDataObjectDeserializer()
                            .getObject()
                            .orElseThrow(() -> new IllegalStateException("No session in event"));

                    String orderIdStr = session.getMetadata().get("orderId");
                    if (orderIdStr == null) {
                        log.warn("Stripe checkout.session.completed without orderId metadata: {}", session.getId());
                        return Mono.just(ResponseEntity.ok("no orderId"));
                    }
                    UUID orderId = UUID.fromString(orderIdStr);
                    return orderService.markPaid(orderId, session.getPaymentIntent())
                            .doOnSuccess(o -> log.info("Order {} confirmed via Stripe webhook", orderId))
                            .thenReturn(ResponseEntity.ok("ok"));
                })
                .onErrorResume(SecurityException.class, ex -> {
                    log.warn("Rejected Stripe webhook: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad signature"));
                });
    }
}
