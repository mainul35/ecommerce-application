package com.ecommerce.repository;

import com.ecommerce.model.SellerProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface SellerProfileRepository extends ReactiveCrudRepository<SellerProfile, UUID> {

    Mono<SellerProfile> findByUserId(UUID userId);
}
