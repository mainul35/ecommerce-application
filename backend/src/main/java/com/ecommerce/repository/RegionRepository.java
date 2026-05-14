package com.ecommerce.repository;

import com.ecommerce.model.Region;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface RegionRepository extends R2dbcRepository<Region, UUID> {

    Flux<Region> findAllByOrderByNameAsc();

    Flux<Region> findByIsActiveTrueOrderByNameAsc();

    Mono<Region> findByCountryCodeIgnoreCase(String countryCode);
}
