package com.ecommerce.repository;

import com.ecommerce.model.VerificationToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends ReactiveCrudRepository<VerificationToken, UUID> {

    /** Resolve an email link token. */
    Mono<VerificationToken> findBySecret(String secret);

    /** Latest challenge for a user on a channel (the active OTP / email token). */
    Mono<VerificationToken> findFirstByUserIdAndChannelOrderByCreatedAtDesc(
            UUID userId, VerificationToken.VerificationChannel channel);
}
