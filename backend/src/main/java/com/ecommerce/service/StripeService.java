package com.ecommerce.service;

import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.repository.OrderItemRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Wraps the Stripe Checkout API. Two responsibilities:
 *   1. createCheckoutSession - turns an Order into a hosted Stripe Checkout URL
 *   2. parseWebhookEvent      - verifies a Stripe webhook signature and returns the Event
 *
 * Stripe SDK calls are blocking by design. We dispatch them on a bounded
 * elastic scheduler via Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())
 * to avoid blocking the Netty event loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private static final String PLACEHOLDER_PREFIX = "sk_test_PLACEHOLDER";

    private final OrderItemRepository orderItemRepository;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @PostConstruct
    void init() {
        if (secretKey == null || secretKey.startsWith(PLACEHOLDER_PREFIX)) {
            log.warn("Stripe is using PLACEHOLDER keys - real payments will fail. "
                    + "Set STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET env vars.");
            return;
        }
        Stripe.apiKey = secretKey;
        log.info("Stripe initialised with key prefix {}***", secretKey.substring(0, Math.min(8, secretKey.length())));
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.startsWith(PLACEHOLDER_PREFIX);
    }

    /**
     * Build a Stripe Checkout Session for the given order. Returns the hosted-checkout URL
     * the customer should be redirected to. Stripe will POST checkout.session.completed
     * to our webhook on success.
     */
    public Mono<String> createCheckoutSession(Order order) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException(
                    "Stripe is not configured. Set STRIPE_SECRET_KEY env var or use the admin mark-paid flow."));
        }

        return orderItemRepository.findByOrderId(order.getId())
                .collectList()
                .flatMap(items -> Mono.fromCallable(() -> buildSession(order, items))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
                .map(Session::getUrl);
    }

    /**
     * Verify a Stripe webhook signature and return the parsed Event. Throws if the
     * signature does not match the configured webhook secret.
     */
    public Mono<Event> parseWebhookEvent(String payload, String signatureHeader) {
        return Mono.fromCallable(() -> {
            try {
                return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                throw new SecurityException("Invalid Stripe webhook signature", e);
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Refund part (or all) of a captured payment back to the customer's original
     * card. Stripe pushes the money to the card used at checkout; if that card
     * was closed, the card network re-routes it to the customer's bank account.
     *
     * Accepts either a PaymentIntent id ({@code pi_...}) or a Checkout Session id
     * ({@code cs_...}) - the session is resolved to its PaymentIntent first.
     */
    public Mono<Void> refund(String paymentRef, BigDecimal amount) {
        if (!isConfigured()) {
            return Mono.error(new IllegalStateException("Stripe is not configured - cannot refund"));
        }
        return Mono.fromCallable(() -> {
                    String paymentIntentId = paymentRef;
                    if (paymentRef != null && paymentRef.startsWith("cs_")) {
                        paymentIntentId = Session.retrieve(paymentRef).getPaymentIntent();
                    }
                    com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .setAmount(toMinorUnits(amount))
                            .build();
                    com.stripe.model.Refund refund = com.stripe.model.Refund.create(params);
                    log.info("Stripe refund {} created for {} ({} minor units)",
                            refund.getId(), paymentIntentId, refund.getAmount());
                    return refund;
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .then();
    }

    private Session buildSession(Order order, java.util.List<OrderItem> items) throws StripeException {
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                // Stripe interpolates {CHECKOUT_SESSION_ID} on its side
                .setSuccessUrl(successUrl.replace("{CHECKOUT_SESSION_ID}", "{CHECKOUT_SESSION_ID}"))
                .setCancelUrl(cancelUrl.replace("{CHECKOUT_SESSION_ID}", "{CHECKOUT_SESSION_ID}"))
                .putMetadata("orderId", order.getId().toString());

        Flux.fromIterable(items).toIterable().forEach(item ->
                params.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(Long.valueOf(item.getQuantity()))
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(toMinorUnits(item.getPrice()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(item.getProductName())
                                        .build())
                                .build())
                        .build()));

        return Session.create(params.build());
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
