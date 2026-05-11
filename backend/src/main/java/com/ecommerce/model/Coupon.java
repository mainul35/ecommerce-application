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

/**
 * Customer-applied promo code. Validated at checkout against time window,
 * minimum order amount, total uses, and per-user uses. Recorded in
 * {@link CouponUse} on successful redemption.
 *
 * Type semantics:
 *   PERCENTAGE   - {@code value} is a percentage 0 < v <= 100
 *   FIXED        - {@code value} is a dollar amount in (0, subtotal]
 *   FREE_SHIPPING - {@code value} is null; shipping cost set to 0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("coupons")
public class Coupon extends BaseEntity {

    @Column("code")
    private String code;

    @Column("name")
    private String name;

    @Column("type")
    private CouponType type;

    @Column("value")
    private BigDecimal value;

    @Column("min_order_amount")
    private BigDecimal minOrderAmount;

    @Column("max_uses")
    private Integer maxUses;

    @Column("max_uses_per_user")
    private Integer maxUsesPerUser;

    @Column("valid_from")
    private LocalDateTime validFrom;

    @Column("valid_until")
    private LocalDateTime validUntil;

    @Column("is_active")
    private Boolean isActive;

    public enum CouponType { PERCENTAGE, FIXED, FREE_SHIPPING }
}
