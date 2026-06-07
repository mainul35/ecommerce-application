package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCreateRequest {

    @NotNull(message = "escrowTransactionId is required")
    private UUID escrowTransactionId;

    /** Optional: the specific order item the complaint is about. */
    private UUID orderItemId;

    @NotBlank(message = "Reason is required")
    @Size(max = 2000, message = "Reason must be at most 2000 characters")
    private String reason;
}
