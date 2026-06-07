package com.ecommerce.repository;

import com.ecommerce.model.WalletTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends ReactiveCrudRepository<WalletTransaction, UUID> {

    Flux<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Mono<Long> countByWalletId(UUID walletId);
}
