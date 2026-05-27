package com.ecommerce.service.payment;

import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SslCommerzGateway implements PaymentGateway {

    private static final String PLACEHOLDER = "PLACEHOLDER";

    private final ObjectMapper objectMapper;

    @Value("${payment.sslcommerz.store-id:PLACEHOLDER}")
    private String storeId;

    @Value("${payment.sslcommerz.store-pass:PLACEHOLDER}")
    private String storePass;

    @Value("${payment.sslcommerz.sandbox:true}")
    private boolean sandbox;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override public String getGatewayId()       { return "sslcommerz"; }
    @Override public String getDisplayName()      { return "bKash / Nagad / Cards"; }
    @Override public String getDescription()      { return "bKash, Nagad, Rocket, Dutch-Bangla, Visa, Mastercard"; }
    @Override public String getIconClass()        { return "bi-phone-fill"; }
    @Override public List<String> getSupportedCountries() { return List.of("BD"); }
    @Override public boolean isAvailable()        { return !storeId.equals(PLACEHOLDER); }

    private String apiUrl() {
        return sandbox
            ? "https://sandbox.sslcommerz.com/gwprocess/v4/api.php"
            : "https://securepay.sslcommerz.com/gwprocess/v4/api.php";
    }

    @Override
    public Mono<String> createCheckoutSession(Order order) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException(
                "SSLCommerz is not configured. Set SSLCOMMERZ_STORE_ID and SSLCOMMERZ_STORE_PASS."));
        }

        String orderId = order.getId().toString();
        String cusName = "Customer";
        String cusPhone = "0000000000";
        String cusAdd1 = "N/A";
        String cusCity = "N/A";
        String cusCountry = "BD";

        try {
            JsonNode addr = objectMapper.readTree(order.getShippingAddress());
            String first = addr.path("firstName").asText("").trim();
            String last  = addr.path("lastName").asText("").trim();
            cusName    = (first + " " + last).trim();
            if (cusName.isEmpty()) cusName = "Customer";
            cusPhone   = addr.path("phone").asText("0000000000");
            cusAdd1    = addr.path("addressLine1").asText("N/A");
            cusCity    = addr.path("city").asText("N/A");
            cusCountry = addr.path("country").asText("BD");
        } catch (Exception e) {
            log.warn("Could not parse shippingAddress for SSLCommerz: {}", e.getMessage());
        }

        String callbackBase = backendUrl + "/api/payment/sslcommerz/callback?orderId=" + orderId;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id",      storeId);
        form.add("store_passwd",  storePass);
        form.add("total_amount",  order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        form.add("currency",      "USD");
        form.add("tran_id",       orderId);
        form.add("success_url",   callbackBase + "&status=success");
        form.add("fail_url",      callbackBase + "&status=fail");
        form.add("cancel_url",    callbackBase + "&status=cancel");
        form.add("cus_name",      cusName);
        form.add("cus_email",     "customer@example.com");
        form.add("cus_phone",     cusPhone);
        form.add("cus_add1",      cusAdd1);
        form.add("cus_city",      cusCity);
        form.add("cus_country",   cusCountry);
        form.add("shipping_method", "NO");
        form.add("product_name",    "Order " + orderId.substring(0, 8));
        form.add("product_category","General");
        form.add("product_profile", "general");

        return WebClient.create()
            .post()
            .uri(apiUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(form))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(resp -> {
                String status = (String) resp.get("status");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    String url = (String) resp.get("GatewayPageURL");
                    log.info("SSLCommerz session created for order {}", orderId);
                    return Mono.just(url);
                }
                String reason = (String) resp.getOrDefault("failedreason", "unknown");
                log.error("SSLCommerz session failed for order {}: {}", orderId, reason);
                return Mono.error(new BadRequestException("SSLCommerz error: " + reason));
            });
    }
}
