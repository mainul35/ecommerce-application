package com.ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login accepts either an email address (regular customer accounts) or a
 * plain username (e.g. the bootstrapped {@code admin} account). Format
 * validation is intentionally not enforced here - the email vs. username
 * distinction is handled by user creation flows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
