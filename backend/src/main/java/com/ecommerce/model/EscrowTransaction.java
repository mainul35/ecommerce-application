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
 * Marketplace escrow: one row per (order, seller) group. Funds captured at
 * checkout are HELD here and only released to the seller's wallet after the
 * buyer confirms receipt or the protection window expires - unless a dispute
 * or return claws part (or all) of it back to the buyer first.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("escrow_transactions")
public class EscrowTransaction extends BaseEntity {

    @Column("order_id")
    private UUID orderId;

    /** Seller (users.id with VENDOR role) this slice of the order belongs to. NULL = platform-owned items. */
    @Column("seller_id")
    private UUID sellerId;

    /** Amount held for this seller group: sum(item price x qty) minus proportional coupon share. */
    @Column("amount")
    private BigDecimal amount;

    /** Cumulative amount refunded back to the buyer (returns / dispute resolutions). */
    @Column("refunded_amount")
    private BigDecimal refundedAmount;

    @Column("currency_code")
    private String currencyCode;

    @Column("status")
    private EscrowStatus status;

    /** PaymentGateway id that captured the funds, e.g. "stripe", "mock". */
    @Column("gateway_id")
    private String gatewayId;

    /** Provider payment reference (Stripe PaymentIntent id etc.) used for refunds. */
    @Column("payment_ref")
    private String paymentRef;

    /** Auto-release deadline; set when the order is DELIVERED. NULL until then. */
    @Column("hold_until")
    private LocalDateTime holdUntil;

    @Column("released_at")
    private LocalDateTime releasedAt;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum EscrowStatus implements NumericEnum {
        /** Funds captured, waiting for delivery confirmation / window expiry. */
        HELD(0),
        /** Remainder (amount - refundedAmount) credited to the seller. Terminal. */
        RELEASED(1),
        /** An open dispute freezes auto-release until staff resolves it. */
        DISPUTED(2),
        /** Fully refunded to the buyer. Terminal. */
        REFUNDED(3);

        private final int code;
    }
}
