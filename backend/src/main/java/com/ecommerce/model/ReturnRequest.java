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
 * Per-item partial return: a buyer may return any subset (with quantity) of
 * the items bought in one checkout, while that seller's escrow is still HELD.
 * The refund amount is the item's effective unit price x quantity minus a
 * proportional share of any order-level coupon discount.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("return_requests")
public class ReturnRequest extends BaseEntity {

    @Column("order_id")
    private UUID orderId;

    @Column("order_item_id")
    private UUID orderItemId;

    /** Escrow row (seller group) the refund is clawed back from. */
    @Column("escrow_transaction_id")
    private UUID escrowTransactionId;

    @Column("requested_by_user_id")
    private UUID requestedByUserId;

    @Column("quantity")
    private Integer quantity;

    @Column("reason")
    private String reason;

    @Column("status")
    private ReturnStatus status;

    /** Computed at request time; executed at approval. */
    @Column("refund_amount")
    private BigDecimal refundAmount;

    @Column("refund_destination")
    private RefundDestination refundDestination;

    @Column("resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    @Column("rejection_reason")
    private String rejectionReason;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum ReturnStatus implements NumericEnum {
        REQUESTED(0),
        /** Approved and refund executed. Terminal. */
        REFUNDED(1),
        REJECTED(2),
        /** Withdrawn by the buyer before a decision. Terminal. */
        CANCELLED(3);

        private final int code;
    }

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum RefundDestination implements NumericEnum {
        /** Refunded through the original payment gateway (e.g. Stripe -> card). */
        GATEWAY(0),
        /** Credited to the buyer's in-app wallet. */
        WALLET(1);

        private final int code;
    }
}
