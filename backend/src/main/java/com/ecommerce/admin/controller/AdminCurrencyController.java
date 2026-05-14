package com.ecommerce.admin.controller;

import com.ecommerce.dto.CurrencyDto;
import com.ecommerce.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/currencies")
@RequiredArgsConstructor
@Tag(name = "Admin - Currencies", description = "Currency catalog and base-currency management")
public class AdminCurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    @Operation(summary = "List all currencies (active and inactive)")
    public Flux<CurrencyDto> list() {
        return currencyService.listAll();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a currency by ISO code")
    public Mono<CurrencyDto> get(@PathVariable String code) {
        return currencyService.findByCode(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new currency")
    public Mono<CurrencyDto> create(@Valid @RequestBody CurrencyDto dto) {
        return currencyService.create(dto);
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update a currency (name, symbol, exchange rate, active)")
    public Mono<CurrencyDto> update(@PathVariable String code, @Valid @RequestBody CurrencyDto dto) {
        return currencyService.update(code, dto);
    }

    @PostMapping("/{code}/set-base")
    @Operation(summary = "Make this currency the base currency (clears the previous base)")
    public Mono<CurrencyDto> setBase(@PathVariable String code) {
        return currencyService.setBase(code);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a currency. Refused if it is the base currency.")
    public Mono<Void> delete(@PathVariable String code) {
        return currencyService.delete(code);
    }
}
