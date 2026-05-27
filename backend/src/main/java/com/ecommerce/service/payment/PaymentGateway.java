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
}
