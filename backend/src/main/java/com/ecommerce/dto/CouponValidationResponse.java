package com.ecommerce.dto;

import com.ecommerce.model.Coupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result of a coupon-validation preview. {@code valid=true} means the coupon
 * COULD be applied to this cart at this moment; the order-create flow re-runs
 * the same checks server-side to prevent racy "spent the coupon between
 * preview and submit" bugs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationResponse {

    private boolean valid;

    /** Human-readable reason on failure, e.g. "Coupon expired". Null on success. */
    private String message;

    /** Echo of the code (uppercased / canonical form) on success. */
    private String code;

    private Coupon.CouponType type;

    /** Cart subtotal after item-level discounts. Always populated. */
    private BigDecimal subtotal;

    /** Saving the coupon would yield. Zero on failure. */
    private BigDecimal discountAmount;

    /** Subtotal minus the discount. */
    private BigDecimal finalAmount;
}
