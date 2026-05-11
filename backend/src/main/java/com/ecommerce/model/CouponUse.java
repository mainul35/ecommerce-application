package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Redemption ledger entry. The unique (coupon_id, order_id) constraint
 * means re-delivery of a Stripe webhook can never double-record the same
 * coupon use.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("coupon_uses")
public class CouponUse extends BaseEntity {

    @Column("coupon_id")
    private UUID couponId;

    @Column("user_id")
    private UUID userId;

    @Column("order_id")
    private UUID orderId;

    @Column("used_at")
    private LocalDateTime usedAt;
}
