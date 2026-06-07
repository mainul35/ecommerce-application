package com.ecommerce.repository;

import com.ecommerce.model.Wallet;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface WalletRepository extends ReactiveCrudRepository<Wallet, UUID> {

    Mono<Wallet> findByUserId(UUID userId);
}
