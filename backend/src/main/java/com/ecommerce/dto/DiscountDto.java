package com.ecommerce.dto;

import com.ecommerce.model.Discount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DiscountDto {

    private UUID id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private Discount.DiscountType type;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.01", message = "Value must be greater than 0")
    private BigDecimal value;

    @NotNull(message = "Scope is required")
    private Discount.DiscountScope scope;

    /** Required when scope is PRODUCT or CATEGORY; must be null when scope is SITEWIDE. */
    private UUID scopeTargetId;

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
