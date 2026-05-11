package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileUpdateRequest {

    @NotBlank(message = "Email/username is required")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    /** Current password - required only when changing password. */
    private String currentPassword;

    /** Optional new password. If blank/null, password is not changed. */
    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
