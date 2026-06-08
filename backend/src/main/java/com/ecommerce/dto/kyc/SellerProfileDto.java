package com.ecommerce.dto.kyc;

import com.ecommerce.model.SellerProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProfileDto {
    private UUID id;
    private UUID userId;
    private SellerProfile.SellerType sellerType;
    private String legalName;
    private LocalDate dateOfBirth;
    private String phone;
    private SellerProfile.IdDocumentType idDocumentType;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String countryCode;
}
