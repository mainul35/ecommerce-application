package com.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyDto {

    @NotBlank(message = "Code is required")
    @Size(min = 3, max = 3, message = "Code must be exactly 3 characters (ISO 4217)")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Exchange rate is required")
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be greater than 0")
    private BigDecimal exchangeRate;

    private Boolean isBase;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
