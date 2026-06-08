package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Durable seller data captured during registration. This is normal account
 * data (kept like any profile) - the transient verification evidence lives
 * in kyc_cases / kyc_documents and is purged per the 72h retention rule.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("seller_profiles")
public class SellerProfile extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    @Column("seller_type")
    private SellerType sellerType;

    /** Full legal name as printed on the photo ID - matched against OCR. */
    @Column("legal_name")
    private String legalName;

    @Column("date_of_birth")
    private LocalDate dateOfBirth;

    @Column("phone")
    private String phone;

    @Column("id_document_type")
    private IdDocumentType idDocumentType;

    @Column("address_line1")
    private String addressLine1;

    @Column("address_line2")
    private String addressLine2;

    @Column("city")
    private String city;

    @Column("state")
    private String state;

    @Column("postal_code")
    private String postalCode;

    @Column("country_code")
    private String countryCode;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum SellerType implements NumericEnum {
        /** Registered business / professional vendor. */
        BUSINESS(0),
        /** Individual selling their own (e.g. used) items. */
        INDIVIDUAL(1);

        private final int code;
    }

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum IdDocumentType implements NumericEnum {
        NATIONAL_ID(0),
        PASSPORT(1),
        DRIVING_LICENSE(2);

        private final int code;
    }
}
