package com.ecommerce.controller;

import com.ecommerce.dto.PaymentGatewayDto;
import com.ecommerce.service.payment.PaymentGatewayRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/payment-gateways")
@RequiredArgsConstructor
@Tag(name = "Payment Gateways", description = "Lists available payment methods by country")
public class PaymentGatewayController {

    private final PaymentGatewayRegistry registry;

    @GetMapping
    @Operation(summary = "Get available payment gateways for a country code")
    public Mono<List<PaymentGatewayDto>> getForCountry(
            @RequestParam(defaultValue = "") String countryCode) {
        return Mono.just(
                registry.listForCountry(countryCode).stream()
                        .map(g -> new PaymentGatewayDto(
                                g.getGatewayId(), g.getDisplayName(),
                                g.getDescription(), g.getIconClass(),
                                g.isAvailable()))
                        .toList());
    }
}
