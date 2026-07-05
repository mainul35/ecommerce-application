package com.ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body for the token refresh endpoints - carries a previously issued refresh token. */
@Data
public class RefreshRequest {

    @NotBlank
    private String refreshToken;
}
