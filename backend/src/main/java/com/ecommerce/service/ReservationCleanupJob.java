package com.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically deletes reservations whose {@code expires_at} has passed and
 * marks the parent order as CANCELLED. Without this, expired but un-paid
 * orders would still appear PENDING and stock would still appear reserved
 * to the SQL view of "active reservations" (no - the WHERE expires_at > NOW()
 * filter excludes them; this job is the bookkeeping cleanup so orders don't
 * sit in PENDING forever).
 *
 * subscribe() is acceptable here for the same reason as AdminBootstrap: this
 * is a scheduled job, not a request handler - the chain must be terminated
 * to fire and there is no upstream pipeline to compose with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupJob {

    private final DatabaseClient db;

    @Scheduled(fixedDelayString = "${reservation.cleanup-interval-ms:300000}",
               initialDelayString = "${reservation.cleanup-initial-delay-ms:60000}")
    public void cleanup() {
        // Single statement: delete expired reservations, then cancel any orders
        // whose reservations all just expired (and were still PENDING).
        String deleteExpired = """
                WITH expired AS (
                    DELETE FROM stock_reservations
                    WHERE expires_at < NOW()
                    RETURNING order_id
                )
                UPDATE orders o
                SET status              = 'CANCELLED',
                    cancelled_at        = NOW(),
                    cancellation_reason = 'Reservation expired'
                FROM (SELECT DISTINCT order_id FROM expired) e
                WHERE o.id = e.order_id
                  AND o.status = 'PENDING'
                """;

        db.sql(deleteExpired)
                .fetch()
                .rowsUpdated()
                .subscribe(
                        cancelled -> {
                            if (cancelled != null && cancelled > 0) {
                                log.info("Reservation cleanup: cancelled {} expired order(s)", cancelled);
                            }
                        },
                        err -> log.error("Reservation cleanup failed", err));
    }
}
