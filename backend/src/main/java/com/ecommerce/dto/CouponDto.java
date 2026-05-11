package com.ecommerce.dto;

import com.ecommerce.model.Coupon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDto {

    private UUID id;

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    private String code;

    private String name;

    @NotNull(message = "Type is required")
    private Coupon.CouponType type;

    /** Required for PERCENTAGE/FIXED, must be null for FREE_SHIPPING. */
    private BigDecimal value;

    private BigDecimal minOrderAmount;
    private Integer maxUses;
    private Integer maxUsesPerUser;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
