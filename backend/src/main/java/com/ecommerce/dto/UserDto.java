package com.ecommerce.dto;

import com.ecommerce.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private User.UserRole role;
    private Boolean isActive;
    private Boolean emailVerified;
    private String phone;
    private Boolean phoneVerified;
    /** e-KYC outcome: true once identity verification passed (seller capability). */
    private Boolean idVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
