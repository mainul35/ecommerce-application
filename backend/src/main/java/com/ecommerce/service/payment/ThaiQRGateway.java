package com.ecommerce.service.payment;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CurrencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThaiQRGateway implements PaymentGateway {

    private static final String PLACEHOLDER = "PLACEHOLDER";
    private static final String OMISE_API   = "https://api.omise.co";

    private final ObjectMapper objectMapper;
    private final CurrencyRepository currencyRepository;

    @Value("${payment.omise.public-key:PLACEHOLDER}")
    private String publicKey;

    @Value("${payment.omise.secret-key:PLACEHOLDER}")
    private String secretKey;

    @Override public String getGatewayId()       { return "omise"; }
    @Override public String getDisplayName()      { return "PromptPay / Cards (Omise)"; }
    @Override public String getDescription()      { return "Thai QR PromptPay, TrueMoney Wallet, Visa, Mastercard"; }
    @Override public String getIconClass()        { return "bi-qr-code-scan"; }
    @Override public List<String> getSupportedCountries() { return List.of("TH"); }
    @Override public boolean isAvailable()        { return !secretKey.equals(PLACEHOLDER); }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException(
                "Omise is not configured. Set OMISE_SECRET_KEY."));
        }

        return currencyRepository.findById("THB")
            .defaultIfEmpty(buildFallbackThb())
            .flatMap(thbCurrency -> {
                // Omise amounts are in satang (THB * 100)
                long amountSatang = order.getTotalAmount()
                    .multiply(thbCurrency.getExchangeRate())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

                String orderId = order.getId().toString();
                String title   = "Order " + orderId.substring(0, 8);

                Map<String, Object> body = Map.of(
                    "amount",   amountSatang,
                    "currency", "thb",
                    "title",    title,
                    "multiple", false
                );

                try {
                    String bodyStr     = objectMapper.writeValueAsString(body);
                    String credentials = Base64.getEncoder().encodeToString(
                        (secretKey + ":").getBytes(StandardCharsets.UTF_8));

                    return WebClient.create(OMISE_API)
                        .post()
                        .uri("/links")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(bodyStr)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .flatMap(resp -> {
                            String paymentUri = (String) resp.get("payment_uri");
                            if (paymentUri != null) {
                                log.info("Omise link created for order {}: {}", orderId, paymentUri);
                                return Mono.just(paymentUri);
                            }
                            log.error("Omise link creation failed for order {}: {}", orderId, resp);
                            return Mono.error(new BadRequestException("Omise link creation failed"));
                        });
                } catch (Exception e) {
                    return Mono.error(new BadRequestException("Omise request build failed: " + e.getMessage()));
                }
            });
    }

    private com.ecommerce.model.Currency buildFallbackThb() {
        return com.ecommerce.model.Currency.builder()
            .code("THB")
            .name("Thai Baht")
            .symbol("฿")
            .exchangeRate(BigDecimal.valueOf(36))
            .isBase(false)
            .isActive(true)
            .build();
    }
}
