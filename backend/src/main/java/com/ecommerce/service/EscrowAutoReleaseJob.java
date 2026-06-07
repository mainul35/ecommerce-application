package com.ecommerce.service;

import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.repository.EscrowTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Releases HELD escrow rows whose buyer-protection window has expired without
 * a confirmation, dispute, or return - the "silence means satisfied" rule.
 * DISPUTED rows are untouched: staff resolution is the only way out of those.
 *
 * Goes through EscrowService.release (not raw SQL) because releasing must
 * also credit the seller's wallet and journal the payout.
 *
 * subscribe() is acceptable here for the same reason as ReservationCleanupJob:
 * scheduled jobs have no upstream pipeline to compose with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowAutoReleaseJob {

    private final EscrowTransactionRepository escrowRepository;
    private final EscrowService escrowService;

    @Scheduled(fixedDelayString = "${escrow.release-interval-ms:300000}",
               initialDelayString = "${escrow.release-initial-delay-ms:90000}")
    public void releaseExpired() {
        escrowRepository.findByStatusAndHoldUntilBefore(
                        EscrowTransaction.EscrowStatus.HELD, LocalDateTime.now())
                .concatMap(escrowService::release)
                .count()
                .subscribe(
                        released -> {
                            if (released != null && released > 0) {
                                log.info("Escrow auto-release: released {} expired hold(s)", released);
                            }
                        },
                        err -> log.error("Escrow auto-release failed", err));
    }
}
