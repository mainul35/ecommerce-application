package com.ecommerce.repository;

import com.ecommerce.model.Discount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface DiscountRepository extends R2dbcRepository<Discount, UUID> {

    /**
     * All discounts that are currently live: active + inside their time window.
     * Caller filters by scope/target in memory (the table is small).
     */
    @Query("SELECT * FROM discounts " +
           "WHERE is_active = true " +
           "  AND (starts_at IS NULL OR starts_at <= NOW()) " +
           "  AND (ends_at   IS NULL OR ends_at   >  NOW())")
    Flux<Discount> findActive();

    Flux<Discount> findAllByOrderByCreatedAtDesc();

    Flux<Discount> findByScopeAndScopeTargetIdOrderByCreatedAtDesc(
            Discount.DiscountScope scope, UUID scopeTargetId);
}
