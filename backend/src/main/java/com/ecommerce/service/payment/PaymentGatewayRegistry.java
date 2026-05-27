package com.ecommerce.service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects all PaymentGateway beans and routes by gateway ID or country code.
 *
 * Gateway priority order: country-specific gateways first (sorted by ID),
 * then global gateways ("*"), mock always last.
 */
@Slf4j
@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGateway> byId;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.byId = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::getGatewayId, Function.identity()));
        log.info("Registered {} payment gateways: {}", gateways.size(),
                gateways.stream().map(PaymentGateway::getGatewayId).collect(Collectors.joining(", ")));
    }

    public Optional<PaymentGateway> find(String gatewayId) {
        return Optional.ofNullable(byId.get(gatewayId));
    }

    /**
     * Returns only configured gateways for the country — used by CheckoutController
     * to route actual payment sessions.
     */
    public List<PaymentGateway> availableForCountry(String countryCode) {
        return listForCountry(countryCode).stream()
                .filter(PaymentGateway::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Returns ALL gateways that support the country (configured or not), ordered:
     * country-specific first, then global ("*"), mock always last.
     * Used by the payment-selector UI so customers always see the local gateways,
     * even when credentials haven't been wired up yet.
     */
    public List<PaymentGateway> listForCountry(String countryCode) {
        String upper = countryCode == null ? "" : countryCode.toUpperCase();
        return byId.values().stream()
                .filter(g -> g.getSupportedCountries().contains("*")
                        || g.getSupportedCountries().contains(upper))
                .sorted(Comparator
                        .<PaymentGateway, Integer>comparing(g -> g.getGatewayId().equals("mock") ? 1 : 0)
                        .thenComparing(g -> g.getSupportedCountries().contains("*") ? 1 : 0)
                        .thenComparing(PaymentGateway::getGatewayId))
                .collect(Collectors.toList());
    }
}
