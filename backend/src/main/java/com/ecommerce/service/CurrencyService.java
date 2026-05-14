package com.ecommerce.service;

import com.ecommerce.dto.CurrencyDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CurrencyMapper;
import com.ecommerce.model.Currency;
import com.ecommerce.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Currency CRUD plus the "set base currency" operation.
 *
 * Setting a new base currency is implemented as a transaction: clear the
 * is_base flag everywhere, then set it on the target row. The partial
 * unique index uq_currencies_one_base would otherwise reject a second
 * is_base=true row.
 */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private static final String NOT_FOUND = "Currency not found";

    private final CurrencyRepository currencyRepository;
    private final CurrencyMapper currencyMapper;
    private final DatabaseClient db;

    public Flux<CurrencyDto> listAll() {
        return currencyRepository.findAllByOrderByCodeAsc().map(currencyMapper::toDto);
    }

    public Flux<CurrencyDto> listActive() {
        return currencyRepository.findByIsActiveTrueOrderByCodeAsc().map(currencyMapper::toDto);
    }

    public Mono<CurrencyDto> findByCode(String code) {
        return currencyRepository.findById(code.toUpperCase())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .map(currencyMapper::toDto);
    }

    public Mono<CurrencyDto> findBase() {
        return currencyRepository.findBase()
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("No base currency configured")))
                .map(currencyMapper::toDto);
    }

    public Mono<CurrencyDto> create(CurrencyDto dto) {
        Currency entity = currencyMapper.toEntity(dto);
        entity.setCode(dto.getCode().toUpperCase());
        if (entity.getIsActive() == null) entity.setIsActive(true);
        if (entity.getIsBase() == null) entity.setIsBase(false);
        // Base flag swap is a separate operation - reject creating a row with isBase=true here.
        if (Boolean.TRUE.equals(entity.getIsBase())) {
            return Mono.error(new BadRequestException(
                    "Use the dedicated 'set as base' action to change the base currency."));
        }
        return currencyRepository.save(entity).map(currencyMapper::toDto);
    }

    public Mono<CurrencyDto> update(String code, CurrencyDto dto) {
        return currencyRepository.findById(code.toUpperCase())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> {
                    existing.setName(dto.getName());
                    existing.setSymbol(dto.getSymbol());
                    existing.setExchangeRate(dto.getExchangeRate());
                    if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
                    // isBase intentionally ignored - use setBase()
                    return currencyRepository.save(existing);
                })
                .map(currencyMapper::toDto);
    }

    /**
     * Atomically swap the base currency: clear is_base on the previous base row,
     * set is_base on the target. Sets target's exchange_rate to 1.0 (a base
     * currency, by definition, is the unit of measure).
     */
    @Transactional
    public Mono<CurrencyDto> setBase(String code) {
        String upper = code.toUpperCase();
        return currencyRepository.findById(upper)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(target -> db.sql("UPDATE currencies SET is_base = FALSE WHERE is_base = TRUE")
                        .fetch().rowsUpdated()
                        .then(db.sql("UPDATE currencies SET is_base = TRUE, exchange_rate = 1.0 WHERE code = :code")
                                .bind("code", upper)
                                .fetch().rowsUpdated())
                        .then(currencyRepository.findById(upper)))
                .map(currencyMapper::toDto);
    }

    public Mono<Void> delete(String code) {
        String upper = code.toUpperCase();
        return currencyRepository.findById(upper)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(c -> {
                    if (Boolean.TRUE.equals(c.getIsBase())) {
                        return Mono.error(new BadRequestException(
                                "Cannot delete the base currency. Set another currency as base first."));
                    }
                    return currencyRepository.delete(c);
                });
    }
}
