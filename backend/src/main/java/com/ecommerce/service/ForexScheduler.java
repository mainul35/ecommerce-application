package com.ecommerce.service;

import com.ecommerce.dto.ForexRateResponse;
import com.ecommerce.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForexScheduler {

    private final CurrencyRepository currencyRepository;
    private final DatabaseClient db;
    private final org.springframework.web.reactive.function.client.WebClient forexWebClient;

    @Value("${forex.markup-percent:0.5}")
    private double markupPercent;

    /** Runs daily at 00:01 to refresh all non-base exchange rates. */
    @Scheduled(cron = "${forex.cron:0 1 0 * * *}")
    public void refreshOnSchedule() {
        log.info("Scheduled forex rate refresh starting");
        buildRefreshPipeline().subscribe(
                null,
                err -> log.error("Scheduled forex refresh failed: {}", err.getMessage()),
                () -> log.info("Scheduled forex rates refreshed (markup: {}%)", markupPercent)
        );
    }

    /** Also runs once at startup so rates are current without waiting until midnight. */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("Startup forex rate refresh starting");
        buildRefreshPipeline().subscribe(
                null,
                err -> log.warn("Startup forex refresh failed (non-fatal): {}", err.getMessage()),
                () -> log.info("Startup forex rates refreshed (markup: {}%)", markupPercent)
        );
    }

    private Mono<Void> buildRefreshPipeline() {
        BigDecimal multiplier = BigDecimal.valueOf(1.0 + markupPercent / 100.0);

        return currencyRepository.findBase()
                .flatMap(base -> forexWebClient.get()
                        .uri(uri -> uri.queryParam("from", base.getCode()).build())
                        .retrieve()
                        .bodyToMono(ForexRateResponse.class)
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))))
                .flatMapMany(response -> Flux.fromIterable(response.getRates().entrySet()))
                .flatMap(entry -> {
                    String code = entry.getKey();
                    BigDecimal displayRate = entry.getValue()
                            .multiply(multiplier)
                            .setScale(6, RoundingMode.HALF_UP);
                    return db.sql("""
                            UPDATE currencies
                               SET exchange_rate = :rate,
                                   updated_at    = NOW()
                             WHERE code    = :code
                               AND is_base = FALSE
                            """)
                            .bind("rate", displayRate)
                            .bind("code", code)
                            .fetch().rowsUpdated()
                            .doOnNext(n -> { if (n > 0) log.debug("Updated {} → {}", code, displayRate); });
                })
                .then();
    }
}
