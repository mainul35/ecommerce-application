package com.ecommerce.repository;

import com.ecommerce.model.ReturnRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends ReactiveCrudRepository<ReturnRequest, UUID> {

    Flux<ReturnRequest> findByOrderId(UUID orderId);

    Flux<ReturnRequest> findByRequestedByUserIdOrderByCreatedAtDesc(UUID userId);

    Flux<ReturnRequest> findByStatus(ReturnRequest.ReturnStatus status, Pageable pageable);

    Mono<Long> countByStatus(ReturnRequest.ReturnStatus status);

    Mono<ReturnRequest> findByOrderItemIdAndStatus(UUID orderItemId, ReturnRequest.ReturnStatus status);
}
