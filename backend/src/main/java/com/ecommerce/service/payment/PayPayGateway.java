package com.ecommerce.service.payment;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CurrencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayPayGateway implements PaymentGateway {

    private static final String PLACEHOLDER = "PLACEHOLDER";

    private final ObjectMapper objectMapper;
    private final CurrencyRepository currencyRepository;

    @Value("${payment.paypay.api-key:PLACEHOLDER}")
    private String apiKey;

    @Value("${payment.paypay.api-secret:PLACEHOLDER}")
    private String apiSecret;

    @Value("${payment.paypay.merchant-id:PLACEHOLDER}")
    private String merchantId;

    @Value("${payment.paypay.sandbox:true}")
    private boolean sandbox;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Override public String getGatewayId()       { return "paypay"; }
    @Override public String getDisplayName()      { return "PayPay"; }
    @Override public String getDescription()      { return "Pay with PayPay QR code or app"; }
    @Override public String getIconClass()        { return "bi-qr-code"; }
    @Override public List<String> getSupportedCountries() { return List.of("JP"); }
    @Override public boolean isAvailable()        { return !apiKey.equals(PLACEHOLDER); }

    private String baseUrl() {
        return sandbox ? "https://stg.paypay.ne.jp" : "https://api.paypay.ne.jp";
    }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException(
                "PayPay is not configured. Set PAYPAY_API_KEY, PAYPAY_API_SECRET."));
        }

        return currencyRepository.findById("JPY")
            .defaultIfEmpty(buildFallbackJpy())
            .flatMap(jpyCurrency -> {
                int amountJpy = order.getTotalAmount()
                    .multiply(jpyCurrency.getExchangeRate())
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();

                String orderId   = order.getId().toString();
                String shortId   = orderId.substring(0, 8);
                String redirectUrl = backendUrl + "/api/payment/paypay/callback?orderId=" + orderId;

                Map<String, Object> body = Map.of(
                    "merchantPaymentId", orderId,
                    "codeType",          "ORDER_QR",
                    "orderDescription",  "Order " + shortId,
                    "amount",            Map.of("amount", amountJpy, "currency", "JPY"),
                    "orderItems",        List.of(Map.of(
                        "name",      "Order " + shortId,
                        "quantity",  1,
                        "unitPrice", Map.of("amount", amountJpy, "currency", "JPY")
                    )),
                    "redirectUrl",  redirectUrl,
                    "redirectType", "WEB_LINK"
                );

                try {
                    String path    = "/v1/codes";
                    String bodyStr = objectMapper.writeValueAsString(body);
                    HttpHeaders headers = buildHeaders("POST", path, bodyStr);

                    return WebClient.create(baseUrl())
                        .post()
                        .uri(path)
                        .headers(h -> h.addAll(headers))
                        .bodyValue(bodyStr)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .flatMap(resp -> extractUrl(resp, orderId));
                } catch (JsonProcessingException e) {
                    return Mono.error(new BadRequestException("PayPay request serialisation failed: " + e.getMessage()));
                }
            });
    }

    @SuppressWarnings("unchecked")
    private Mono<String> extractUrl(Map<String, Object> resp, String orderId) {
        Map<String, Object> resultInfo = (Map<String, Object>) resp.get("resultInfo");
        if (resultInfo != null && "SUCCESS".equals(resultInfo.get("code"))) {
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            log.info("PayPay QR created for order {}", orderId);
            return Mono.just((String) data.get("url"));
        }
        String code = resultInfo != null ? (String) resultInfo.get("code") : "UNKNOWN";
        log.error("PayPay error for order {}: {}", orderId, code);
        return Mono.error(new BadRequestException("PayPay error: " + code));
    }

    public HttpHeaders buildHeaders(String method, String path, String body) {
        try {
            String nonce       = UUID.randomUUID().toString();
            String epoch       = String.valueOf(Instant.now().getEpochSecond());
            String hashPayload = (body == null || body.isBlank()) ? "empty" : sha256Base64(body);
            String message     = hashPayload + "\n" + method + "\n" + path + "\n" + nonce + "\n" + epoch;
            String signature   = hmacSha256Base64(message, apiSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization",
                "hmac OPA-Auth:" + apiKey + ":" + nonce + ":" + epoch + ":" + signature);
            return headers;
        } catch (Exception e) {
            throw new IllegalStateException("PayPay HMAC signing failed", e);
        }
    }

    private static String sha256Base64(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(
            digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hmacSha256Base64(String message, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(
            mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    /** Fallback when JPY isn't in the currencies table — approximate rate 150 JPY/USD. */
    private com.ecommerce.model.Currency buildFallbackJpy() {
        return com.ecommerce.model.Currency.builder()
            .code("JPY")
            .name("Japanese Yen")
            .symbol("¥")
            .exchangeRate(BigDecimal.valueOf(150))
            .isBase(false)
            .isActive(true)
            .build();
    }
}
