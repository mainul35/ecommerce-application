package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhoneSendRequest {

    @NotBlank(message = "Phone number is required")
    @Size(max = 30)
    @Pattern(regexp = "[+0-9 ()-]{6,30}", message = "Enter a valid phone number")
    private String phone;
}
