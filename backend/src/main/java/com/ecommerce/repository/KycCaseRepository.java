package com.ecommerce.repository;

import com.ecommerce.model.KycCase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface KycCaseRepository extends ReactiveCrudRepository<KycCase, UUID> {

    /** The user's in-flight case (DRAFT/SUBMITTED/CHECKING/IN_REVIEW), if any. */
    @Query("SELECT * FROM kyc_cases WHERE user_id = :userId AND status IN (0, 1, 2, 3)")
    Mono<KycCase> findActiveByUserId(UUID userId);

    Flux<KycCase> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Flux<KycCase> findByStatus(KycCase.KycStatus status, Pageable pageable);

    Mono<Long> countByStatus(KycCase.KycStatus status);

    /** Retention sweep: evidence past its 72h deadline and not yet purged. */
    @Query("SELECT * FROM kyc_cases WHERE expires_at < :cutoff AND documents_purged_at IS NULL")
    Flux<KycCase> findExpiredUnpurged(LocalDateTime cutoff);
}
