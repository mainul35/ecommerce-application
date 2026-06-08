package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusDto {
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean fullyVerified;
    private String email;
    private String phone;
}
