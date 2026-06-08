package com.ecommerce.dto.kyc;

import com.ecommerce.model.SellerProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProfileRequest {

    @NotNull(message = "Seller type is required")
    private SellerProfile.SellerType sellerType;

    @NotBlank(message = "Legal name is required")
    @Size(max = 200)
    private String legalName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 30)
    private String phone;

    @NotNull(message = "ID document type is required")
    private SellerProfile.IdDocumentType idDocumentType;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 20)
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Pattern(regexp = "[A-Za-z]{2}", message = "Country must be an ISO 3166-1 alpha-2 code")
    private String countryCode;
}
