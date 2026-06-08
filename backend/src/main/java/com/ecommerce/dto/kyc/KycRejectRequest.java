package com.ecommerce.dto.kyc;

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
public class KycRejectRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 2000)
    private String reason;
}
