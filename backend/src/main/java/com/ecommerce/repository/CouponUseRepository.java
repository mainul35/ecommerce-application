package com.ecommerce.repository;

import com.ecommerce.model.CouponUse;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CouponUseRepository extends R2dbcRepository<CouponUse, UUID> {
    // Atomic insert-with-limit-check is done via DatabaseClient in CouponService.
}
