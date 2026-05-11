package com.ecommerce.repository;

import com.ecommerce.model.Coupon;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface CouponRepository extends R2dbcRepository<Coupon, UUID> {

    @Query("SELECT * FROM coupons WHERE LOWER(code) = LOWER(:code)")
    Mono<Coupon> findByCodeIgnoreCase(String code);

    Flux<Coupon> findAllByOrderByCreatedAtDesc();
}
