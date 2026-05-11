package com.ecommerce.repository;

import com.ecommerce.model.OrderItem;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface OrderItemRepository extends R2dbcRepository<OrderItem, UUID> {

    Flux<OrderItem> findByOrderId(UUID orderId);

    Mono<Void> deleteByOrderId(UUID orderId);
}
