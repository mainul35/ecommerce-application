package com.ecommerce.repository;

import com.ecommerce.model.Currency;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CurrencyRepository extends R2dbcRepository<Currency, String> {

    Flux<Currency> findAllByOrderByCodeAsc();

    Flux<Currency> findByIsActiveTrueOrderByCodeAsc();

    @Query("SELECT * FROM currencies WHERE is_base = TRUE LIMIT 1")
    Mono<Currency> findBase();
}
