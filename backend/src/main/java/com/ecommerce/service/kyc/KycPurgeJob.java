package com.ecommerce.service.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces the 72-hour retention cap on KYC evidence: any case whose
 * expires_at has passed gets its files, document rows, and OCR-derived
 * fields purged; undecided cases are marked EXPIRED (the user re-runs KYC).
 *
 * Decision-time purging already covers the normal path - this job is the
 * backstop for cases nobody decided in time.
 *
 * subscribe() is acceptable here for the same reason as the other scheduled
 * jobs: there is no upstream pipeline to compose with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycPurgeJob {

    private final KycService kycService;

    @Scheduled(fixedDelayString = "${kyc.purge-interval-ms:600000}",
               initialDelayString = "${kyc.purge-initial-delay-ms:120000}")
    public void purgeExpiredEvidence() {
        kycService.purgeExpired()
                .subscribe(
                        purged -> {
                            if (purged != null && purged > 0) {
                                log.info("KYC retention sweep: purged evidence of {} case(s)", purged);
                            }
                        },
                        err -> log.error("KYC retention sweep failed", err));
    }
}
