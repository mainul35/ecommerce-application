package com.ecommerce.repository;

import com.ecommerce.model.StockReservation;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface StockReservationRepository extends R2dbcRepository<StockReservation, UUID> {

    Flux<StockReservation> findByOrderId(UUID orderId);

    Mono<Void> deleteByOrderId(UUID orderId);
}
