package com.ecommerce.service.payment;

import com.ecommerce.model.Order;
import com.ecommerce.service.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Delegates to the existing StripeService.
 * StripeService is kept intact because StripeWebhookController depends on it.
 */
@Component
@RequiredArgsConstructor
public class StripeGateway implements PaymentGateway {

    private final StripeService stripeService;

    @Override public String getGatewayId()       { return "stripe"; }
    @Override public String getDisplayName()      { return "Credit / Debit Card"; }
    @Override public String getDescription()      { return "Visa, Mastercard, Amex — powered by Stripe"; }
    @Override public String getIconClass()        { return "bi-credit-card-fill"; }
    @Override public List<String> getSupportedCountries() { return List.of("*"); }
    @Override public boolean isAvailable()        { return stripeService.isConfigured(); }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        return stripeService.createCheckoutSession(order);
    }
}
