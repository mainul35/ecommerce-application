package com.ecommerce.repository;

import com.ecommerce.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, UUID> {

    Flux<Order> findByUserId(UUID userId, Pageable pageable);

    Mono<Long> countByUserId(UUID userId);

    Flux<Order> findByStatus(Order.OrderStatus status, Pageable pageable);

    Mono<Long> countByStatus(Order.OrderStatus status);

    Mono<Order> findByIdAndUserId(UUID id, UUID userId);
}
