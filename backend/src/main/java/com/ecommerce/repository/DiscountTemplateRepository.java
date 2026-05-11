package com.ecommerce.repository;

import com.ecommerce.model.DiscountTemplate;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface DiscountTemplateRepository extends R2dbcRepository<DiscountTemplate, UUID> {

    Flux<DiscountTemplate> findAllByOrderByCreatedAtDesc();
}
