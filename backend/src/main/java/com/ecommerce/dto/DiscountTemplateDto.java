package com.ecommerce.dto;

import com.ecommerce.model.Discount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class DiscountTemplateDto {

    private UUID id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Type is required")
    private Discount.DiscountType type;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.01", message = "Value must be greater than 0")
    private BigDecimal value;

    @Positive(message = "Default duration must be positive")
    private Integer defaultDurationDays;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
