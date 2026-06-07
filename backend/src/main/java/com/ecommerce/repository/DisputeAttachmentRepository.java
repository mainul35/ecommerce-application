package com.ecommerce.repository;

import com.ecommerce.model.DisputeAttachment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface DisputeAttachmentRepository extends ReactiveCrudRepository<DisputeAttachment, UUID> {

    Flux<DisputeAttachment> findByDisputeMessageId(UUID disputeMessageId);
}
