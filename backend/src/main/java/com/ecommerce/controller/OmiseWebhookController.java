package com.ecommerce.controller;

import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Hidden
@RestController
@RequestMapping("/api/webhooks/omise")
@RequiredArgsConstructor
public class OmiseWebhookController {

    private final OrderService orderService;

    @Value("${payment.omise.secret-key:PLACEHOLDER}")
    private String secretKey;

    private static final ResponseEntity<Void> OK = ResponseEntity.ok(null);

    @PostMapping
    public Mono<ResponseEntity<Void>> handleWebhook(
            @RequestBody Map<String, Object> payload) {

        String key = (String) payload.get("key");
        if (!"charge.complete".equals(key)) {
            return Mono.just(OK);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            return Mono.just(OK);
        }

        String chargeId = (String) data.get("id");
        String status   = (String) data.get("status");

        if (!"successful".equalsIgnoreCase(status)) {
            log.info("Omise charge {} status is {}, skipping", chargeId, status);
            return Mono.just(OK);
        }

        // Verify by re-fetching the charge from Omise
        return fetchAndMarkPaid(chargeId)
            .<ResponseEntity<Void>>thenReturn(OK)
            .onErrorResume(e -> {
                log.error("Omise webhook processing failed for charge {}: {}", chargeId, e.getMessage());
                return Mono.just(OK);
            });
    }

    private Mono<Void> fetchAndMarkPaid(String chargeId) {
        String credentials = Base64.getEncoder().encodeToString(
            (secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return WebClient.create("https://api.omise.co")
            .get()
            .uri("/charges/" + chargeId)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(charge -> {
                if (!"successful".equalsIgnoreCase((String) charge.get("status"))) {
                    return Mono.empty();
                }
                // Extract orderId from the description field "Order <uuid>"
                String description = (String) charge.get("description");
                if (description == null) return Mono.empty();
                String[] parts = description.split(" ");
                if (parts.length < 2) return Mono.empty();
                try {
                    UUID orderId = UUID.fromString(parts[parts.length - 1]);
                    return orderService.markPaid(orderId, "omise:" + chargeId)
                        .doOnSuccess(o -> log.info("Omise payment confirmed for order {}", orderId))
                        .then();
                } catch (IllegalArgumentException e) {
                    log.warn("Omise charge {} has invalid orderId in description: {}", chargeId, description);
                    return Mono.empty();
                }
            });
    }
}
