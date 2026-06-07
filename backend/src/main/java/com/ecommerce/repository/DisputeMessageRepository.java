package com.ecommerce.repository;

import com.ecommerce.model.DisputeMessage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface DisputeMessageRepository extends ReactiveCrudRepository<DisputeMessage, UUID> {

    Flux<DisputeMessage> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);
}
