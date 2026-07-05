package com.ecommerce.dto.auth;

import com.ecommerce.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UserDto user;
    /** Short-lived access token (audience-scoped: storefront or admin). */
    private String token;
    /** Longer-lived refresh token; exchange at /api/auth/refresh or /api/admin/auth/refresh. */
    private String refreshToken;
}
