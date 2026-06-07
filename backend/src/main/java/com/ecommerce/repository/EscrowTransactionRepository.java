package com.ecommerce.repository;

import com.ecommerce.model.EscrowTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface EscrowTransactionRepository extends ReactiveCrudRepository<EscrowTransaction, UUID> {

    Flux<EscrowTransaction> findByOrderId(UUID orderId);

    Mono<EscrowTransaction> findByOrderIdAndSellerId(UUID orderId, UUID sellerId);

    Flux<EscrowTransaction> findByStatus(EscrowTransaction.EscrowStatus status, Pageable pageable);

    Mono<Long> countByStatus(EscrowTransaction.EscrowStatus status);

    Flux<EscrowTransaction> findBySellerId(UUID sellerId, Pageable pageable);

    /** Auto-release scan: HELD rows whose protection window has ended. */
    Flux<EscrowTransaction> findByStatusAndHoldUntilBefore(
            EscrowTransaction.EscrowStatus status, LocalDateTime cutoff);
}
