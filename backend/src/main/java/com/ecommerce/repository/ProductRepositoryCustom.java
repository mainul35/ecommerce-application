package com.ecommerce.repository;

import com.ecommerce.model.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ProductRepositoryCustom {

    Flux<Product> searchByName(String search, int limit, long offset);

    Mono<Long> countBySearch(String search);

    Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, int limit, long offset);
}
