package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRejectRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 2000, message = "Reason must be at most 2000 characters")
    private String reason;
}
