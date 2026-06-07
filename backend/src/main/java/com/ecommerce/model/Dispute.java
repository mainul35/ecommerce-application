package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A buyer-opened dispute against a HELD escrow transaction. Carries a
 * conversation thread (dispute_messages) between buyer and seller; either
 * side can ESCALATE to forward the whole thread to admin/support, who
 * resolves by releasing to the seller or refunding the buyer.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("disputes")
public class Dispute extends BaseEntity {

    @Column("escrow_transaction_id")
    private UUID escrowTransactionId;

    /** Denormalized from the escrow row for cheap "disputes of my order" queries. */
    @Column("order_id")
    private UUID orderId;

    /** Optional: the specific item complained about. NULL = whole seller group. */
    @Column("order_item_id")
    private UUID orderItemId;

    @Column("opened_by_user_id")
    private UUID openedByUserId;

    @Column("reason")
    private String reason;

    @Column("status")
    private DisputeStatus status;

    @Column("escalated_at")
    private LocalDateTime escalatedAt;

    @Column("resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column("resolution_note")
    private String resolutionNote;

    /** Amount refunded to the buyer when resolved with a refund. NULL otherwise. */
    @Column("refund_amount")
    private BigDecimal refundAmount;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum DisputeStatus implements NumericEnum {
        /** Buyer <-> seller conversation in progress. */
        OPEN(0),
        /** Forwarded to admin/support for resolution. */
        ESCALATED(1),
        /** Staff sided with the seller - escrow released. Terminal. */
        RESOLVED_RELEASED(2),
        /** Staff sided with the buyer - (partial) refund issued. Terminal. */
        RESOLVED_REFUNDED(3),
        /** Buyer withdrew the dispute - escrow back to HELD. Terminal. */
        WITHDRAWN(4);

        private final int code;
    }
}
