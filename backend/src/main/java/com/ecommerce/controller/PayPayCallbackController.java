package com.ecommerce.controller;

import com.ecommerce.service.OrderService;
import com.ecommerce.service.payment.PayPayGateway;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Hidden
@RestController
@RequestMapping("/api/payment/paypay")
@RequiredArgsConstructor
public class PayPayCallbackController {

    private final OrderService orderService;
    private final PayPayGateway payPayGateway;

    @Value("${payment.paypay.sandbox:true}")
    private boolean sandbox;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/callback")
    public Mono<Void> handleCallback(
            @RequestParam UUID orderId,
            ServerWebExchange exchange) {

        String baseUrl = sandbox ? "https://stg.paypay.ne.jp" : "https://api.paypay.ne.jp";
        String path    = "/v1/codes/payments/" + orderId;

        try {
            var headers = payPayGateway.buildHeaders("GET", path, null);
            return WebClient.create(baseUrl)
                .get()
                .uri(path)
                .headers(h -> h.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(resp -> {
                    if (isCompleted(resp)) {
                        return orderService.markPaid(orderId, "paypay:" + orderId)
                            .doOnSuccess(o -> log.info("PayPay payment confirmed for order {}", orderId))
                            .then();
                    }
                    log.warn("PayPay payment not completed for order {}: {}", orderId, resp.get("resultInfo"));
                    return Mono.empty();
                })
                .then(redirect(exchange, frontendUrl + "/checkout/success?orderId=" + orderId))
                .onErrorResume(e -> {
                    log.error("PayPay callback error for order {}: {}", orderId, e.getMessage());
                    return redirect(exchange, frontendUrl + "/checkout/cancel?orderId=" + orderId);
                });
        } catch (Exception e) {
            log.error("PayPay header build error: {}", e.getMessage());
            return redirect(exchange, frontendUrl + "/checkout/cancel?orderId=" + orderId);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isCompleted(Map<String, Object> resp) {
        Map<String, Object> resultInfo = (Map<String, Object>) resp.get("resultInfo");
        Map<String, Object> data       = (Map<String, Object>) resp.get("data");
        return resultInfo != null && "SUCCESS".equals(resultInfo.get("code"))
            && data != null && "COMPLETED".equals(data.get("status"));
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String url) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(url));
        return exchange.getResponse().setComplete();
    }
}
