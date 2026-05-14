package com.ecommerce.controller;

import com.ecommerce.dto.CurrencyDto;
import com.ecommerce.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Public read access to active currencies. The storefront fetches this once
 * to populate the currency picker and to do client-side price conversion.
 */
@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
@Tag(name = "Currencies", description = "Public currency catalog")
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    @Operation(summary = "List all active currencies (used by the currency picker)")
    public Flux<CurrencyDto> list() {
        return currencyService.listActive();
    }

    @GetMapping("/base")
    @Operation(summary = "Get the configured base currency")
    public Mono<CurrencyDto> base() {
        return currencyService.findBase();
    }
}
