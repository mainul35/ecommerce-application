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

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("orders")
public class Order extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    /** Admin user who created the order on behalf of {@link #userId}. NULL for self-checkout. */
    @Column("placed_by_user_id")
    private UUID placedByUserId;

    @Column("status")
    private OrderStatus status;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("shipping_address")
    private String shippingAddress;

    @Column("billing_address")
    private String billingAddress;

    @Column("payment_method")
    private String paymentMethod;

    @Column("payment_status")
    private PaymentStatus paymentStatus;

    @Column("notes")
    private String notes;

    /** When the stock reservation behind this order expires if unpaid. */
    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("cancelled_at")
    private LocalDateTime cancelledAt;

    @Column("cancellation_reason")
    private String cancellationReason;

    /** Payment provider reference (e.g. Stripe PaymentIntent id). */
    @Column("payment_intent_id")
    private String paymentIntentId;

    @Column("paid_at")
    private LocalDateTime paidAt;

    /** Coupon code redeemed on this order, in canonical (uppercase) form. NULL if no coupon used. */
    @Column("coupon_code")
    private String couponCode;

    @Column("coupon_discount_amount")
    private BigDecimal couponDiscountAmount;

    /** Sum of (effective price after item discounts) * quantity. NULL on legacy rows. */
    @Column("subtotal_amount")
    private BigDecimal subtotalAmount;

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }

    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED
    }
}
