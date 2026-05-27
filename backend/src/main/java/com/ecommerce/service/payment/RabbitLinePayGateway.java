package com.ecommerce.service.payment;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CurrencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitLinePayGateway implements PaymentGateway {

    private static final String PLACEHOLDER = "PLACEHOLDER";

    private final ObjectMapper objectMapper;
    private final CurrencyRepository currencyRepository;

    @Value("${payment.linepay.channel-id:PLACEHOLDER}")
    private String channelId;

    @Value("${payment.linepay.channel-secret:PLACEHOLDER}")
    private String channelSecret;

    @Value("${payment.linepay.sandbox:true}")
    private boolean sandbox;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override public String getGatewayId()       { return "linepay"; }
    @Override public String getDisplayName()      { return "Rabbit LINE Pay"; }
    @Override public String getDescription()      { return "Pay with LINE Pay wallet"; }
    @Override public String getIconClass()        { return "bi-chat-heart-fill"; }
    @Override public List<String> getSupportedCountries() { return List.of("TH"); }
    @Override public boolean isAvailable()        { return !channelId.equals(PLACEHOLDER); }

    private String baseUrl() {
        return sandbox ? "https://sandbox-api-pay.line.me" : "https://api-pay.line.me";
    }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException(
                "LINE Pay is not configured. Set LINEPAY_CHANNEL_ID and LINEPAY_CHANNEL_SECRET."));
        }

        return currencyRepository.findById("THB")
            .defaultIfEmpty(buildFallbackThb())
            .flatMap(thbCurrency -> {
                int amountThb = order.getTotalAmount()
                    .multiply(thbCurrency.getExchangeRate())
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();

                String orderId    = order.getId().toString();
                String shortId    = orderId.substring(0, 8);
                String confirmUrl = backendUrl + "/api/payment/linepay/confirm?orderId=" + orderId;
                String cancelUrl  = frontendUrl + "/checkout/cancel?orderId=" + orderId;

                Map<String, Object> body = Map.of(
                    "amount",   amountThb,
                    "currency", "THB",
                    "orderId",  orderId,
                    "packages", List.of(Map.of(
                        "id",      "1",
                        "amount",  amountThb,
                        "products", List.of(Map.of(
                            "name",     "Order " + shortId,
                            "quantity", 1,
                            "price",    amountThb
                        ))
                    )),
                    "redirectUrls", Map.of(
                        "confirmUrl", confirmUrl,
                        "cancelUrl",  cancelUrl
                    )
                );

                try {
                    String path    = "/v3/payments/request";
                    String bodyStr = objectMapper.writeValueAsString(body);
                    var headers    = buildLinePayHeaders(path, bodyStr);

                    return WebClient.create(baseUrl())
                        .post()
                        .uri(path)
                        .headers(h -> h.addAll(headers))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(bodyStr)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .flatMap(resp -> extractPaymentUrl(resp, orderId));
                } catch (Exception e) {
                    return Mono.error(new BadRequestException("LINE Pay request build failed: " + e.getMessage()));
                }
            });
    }

    @SuppressWarnings("unchecked")
    private Mono<String> extractPaymentUrl(Map<String, Object> resp, String orderId) {
        Object returnCodeObj = resp.get("returnCode");
        String returnCode = null;
        if (returnCodeObj instanceof Map<?, ?> rcMap) {
            returnCode = (String) rcMap.get("returnCode");
        } else if (returnCodeObj instanceof String s) {
            returnCode = s;
        }

        if ("0000".equals(returnCode) || resp.containsKey("info")) {
            Object infoObj = resp.get("info");
            if (infoObj instanceof Map<?, ?> info) {
                Object puObj = info.get("paymentUrl");
                if (puObj instanceof Map<?, ?> pu) {
                    String url = (String) pu.get("web");
                    if (url != null) {
                        log.info("LINE Pay payment URL created for order {}", orderId);
                        return Mono.just(url);
                    }
                }
            }
        }
        log.error("LINE Pay request failed for order {}: {}", orderId, resp);
        return Mono.error(new BadRequestException("LINE Pay error: " + resp.get("returnMessage")));
    }

    org.springframework.http.HttpHeaders buildLinePayHeaders(String path, String body) throws Exception {
        String nonce     = UUID.randomUUID().toString();
        String message   = channelSecret + path + body + nonce;
        String signature = hmacSha256Base64(message, channelSecret);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-LINE-ChannelId",           channelId);
        headers.set("X-LINE-Authorization-Nonce", nonce);
        headers.set("X-LINE-Authorization",       "HMAC-SHA256 " + signature);
        return headers;
    }

    private static String hmacSha256Base64(String message, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(
            mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
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
