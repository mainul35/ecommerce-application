package com.ecommerce.service.payment;

import com.ecommerce.model.Order;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Strategy interface for payment gateways.
 * Each gateway implementation is a Spring bean collected by PaymentGatewayRegistry.
 */
public interface PaymentGateway {

    /** Unique identifier used in API calls, e.g. "stripe", "mock", "sslcommerz". */
    String getGatewayId();

    /** Human-readable name shown in the checkout UI. */
    String getDisplayName();

    /** Short description of what methods are supported. */
    String getDescription();

    /** Bootstrap icon class for the UI, e.g. "bi-credit-card-fill". */
    String getIconClass();

    /**
     * ISO 3166-1 alpha-2 country codes this gateway supports.
     * Return List.of("*") for a globally available gateway.
     */
    List<String> getSupportedCountries();

    /**
     * Returns false if the gateway cannot process payments because
     * required API keys or configuration are missing.
     */
    boolean isAvailable();

    /**
     * Create a hosted-payment session for the given order.
     * Returns a URL the browser should redirect to.
     * Must never block — use Schedulers.boundedElastic() for any blocking SDK calls.
     */
    Mono<String> createCheckoutSession(Order order);

    /**
     * Whether this gateway can push money back to the customer's original
     * payment method. When false, refunds fall back to the in-app wallet.
     */
    default boolean supportsRefunds() {
        return false;
    }

    /**
     * Refund {@code amount} of the payment referenced by {@code paymentRef}
     * (the value stored at markPaid time, e.g. a Stripe PaymentIntent id).
     * Must never block — use Schedulers.boundedElastic() for any blocking SDK calls.
     */
    default Mono<Void> refund(String paymentRef, java.math.BigDecimal amount) {
        return Mono.error(new UnsupportedOperationException(
                getGatewayId() + " does not support refunds"));
    }
}
