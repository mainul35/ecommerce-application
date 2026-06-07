package com.ecommerce.repository;

import com.ecommerce.model.Dispute;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface DisputeRepository extends ReactiveCrudRepository<Dispute, UUID> {

    Flux<Dispute> findByOrderId(UUID orderId);

    Flux<Dispute> findByOpenedByUserIdOrderByCreatedAtDesc(UUID userId);

    Flux<Dispute> findByStatus(Dispute.DisputeStatus status, Pageable pageable);

    Mono<Long> countByStatus(Dispute.DisputeStatus status);

    /** Active (OPEN or ESCALATED) dispute on an escrow transaction, if any. */
    @Query("SELECT * FROM disputes WHERE escrow_transaction_id = :escrowTransactionId AND status IN (0, 1)")
    Mono<Dispute> findActiveByEscrowTransactionId(UUID escrowTransactionId);

    /** Disputes where the user is the seller of the disputed escrow group. */
    @Query("""
            SELECT d.* FROM disputes d
            JOIN escrow_transactions et ON et.id = d.escrow_transaction_id
            WHERE et.seller_id = :sellerId
            ORDER BY d.created_at DESC
            """)
    Flux<Dispute> findBySellerId(UUID sellerId);
}
