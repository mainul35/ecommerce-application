package com.ecommerce.service.payment;

import com.ecommerce.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Development-only gateway — simulates a payment page so the full checkout
 * flow can be tested without real API keys. Always available.
 *
 * The returned URL points to /checkout/mock-pay on the frontend, which
 * has "Pay Now" and "Cancel" buttons wired to /api/payment/mock/{id}/confirm|cancel.
 */
@Slf4j
@Component
public class MockGateway implements PaymentGateway {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override public String getGatewayId()       { return "mock"; }
    @Override public String getDisplayName()      { return "Simulated Payment (Dev)"; }
    @Override public String getDescription()      { return "Test the checkout flow without real payment credentials"; }
    @Override public String getIconClass()        { return "bi-play-circle-fill"; }
    @Override public List<String> getSupportedCountries() { return List.of("*"); }
    @Override public boolean isAvailable()        { return true; }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        String url = frontendUrl + "/checkout/mock-pay?orderId=" + order.getId();
        log.info("Mock checkout session created for order {}: {}", order.getId(), url);
        return Mono.just(url);
    }
}
