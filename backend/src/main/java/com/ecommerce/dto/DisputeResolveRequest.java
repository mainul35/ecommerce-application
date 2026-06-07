package com.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResolveRequest {

    public enum Action {
        /** Seller wins: release remaining escrow to the seller. */
        RELEASE,
        /** Buyer wins: refund (full by default, partial via refundAmount). */
        REFUND
    }

    @NotNull(message = "Action is required")
    private Action action;

    /** Only for REFUND. Defaults to the full remaining held amount. */
    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    private BigDecimal refundAmount;

    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String note;
}
