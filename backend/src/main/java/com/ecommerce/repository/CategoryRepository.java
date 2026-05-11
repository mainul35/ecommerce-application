package com.ecommerce.repository;

import com.ecommerce.model.Category;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface CategoryRepository extends R2dbcRepository<Category, UUID> {

    Flux<Category> findByIsActiveTrue();

    Flux<Category> findByParentIdIsNull();

    Flux<Category> findByParentId(UUID parentId);

    Mono<Category> findBySlug(String slug);

    Mono<Boolean> existsBySlug(String slug);
}
