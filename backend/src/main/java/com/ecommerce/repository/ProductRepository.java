package com.ecommerce.repository;

import com.ecommerce.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ProductRepository extends R2dbcRepository<Product, UUID>, ProductRepositoryCustom {

    Flux<Product> findByIsActiveTrue(Pageable pageable);

    Flux<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId, Pageable pageable);

    Mono<Long> countByCategoryIdAndIsActiveTrue(UUID categoryId);

    Mono<Long> countByIsActiveTrue();

    Mono<Product> findBySku(String sku);

    Mono<Boolean> existsBySku(String sku);

    Flux<Product> findByVendorId(UUID vendorId, Pageable pageable);
}
