package com.ecommerce.controller;

import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Hidden
@RestController
@RequestMapping("/api/payment/sslcommerz")
@RequiredArgsConstructor
public class SslCommerzCallbackController {

    private final OrderService orderService;

    @Value("${payment.sslcommerz.store-id:PLACEHOLDER}")
    private String storeId;

    @Value("${payment.sslcommerz.store-pass:PLACEHOLDER}")
    private String storePass;

    @Value("${payment.sslcommerz.sandbox:true}")
    private boolean sandbox;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** Handles both the customer redirect (GET) and SSLCommerz IPN POST. */
    @GetMapping("/callback")
    @PostMapping("/callback")
    public Mono<Void> handleCallback(
            @RequestParam UUID orderId,
            @RequestParam String status,
            @RequestParam(required = false) String val_id,
            ServerWebExchange exchange) {

        if ("success".equals(status) && val_id != null) {
            return validateWithSslCommerz(val_id)
                .flatMap(valid -> {
                    if (Boolean.TRUE.equals(valid)) {
                        return orderService.markPaid(orderId, "sslcommerz:" + val_id)
                            .doOnSuccess(o -> log.info("SSLCommerz payment confirmed for order {}", orderId))
                            .then();
                    }
                    log.warn("SSLCommerz validation failed for val_id {} order {}", val_id, orderId);
                    return Mono.empty();
                })
                .then(redirect(exchange, frontendUrl + "/checkout/success?orderId=" + orderId));
        }

        log.info("SSLCommerz payment {} for order {}", status, orderId);
        return redirect(exchange, frontendUrl + "/checkout/cancel?orderId=" + orderId);
    }

    private Mono<Boolean> validateWithSslCommerz(String valId) {
        String host = sandbox ? "sandbox.sslcommerz.com" : "securepay.sslcommerz.com";
        String url  = "https://" + host + "/validator/api/validationserverAPI.php"
            + "?val_id=" + valId
            + "&store_id=" + storeId
            + "&store_passwd=" + storePass
            + "&format=json";

        return WebClient.create()
            .get()
            .uri(url)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .map(resp -> {
                String s = (String) resp.get("status");
                return "VALID".equalsIgnoreCase(s) || "VALIDATED".equalsIgnoreCase(s);
            })
            .onErrorReturn(false);
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String url) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(url));
        return exchange.getResponse().setComplete();
    }
}
