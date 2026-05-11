package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin-issued request to provision a MANAGER account. Email here can be
 * a plain username (e.g. "pat") or an actual email; the manager will sign
 * in at /admin/login. Initial password is set by the admin and the
 * manager can change it later via Settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagerCreateRequest {

    @NotBlank(message = "Email or username is required")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Initial password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
