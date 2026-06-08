package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("users")
public class User extends BaseEntity {

    @Column("email")
    private String email;

    @Column("password")
    private String password;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("role")
    private UserRole role;

    @Column("is_active")
    private Boolean isActive;

    @Column("email_verified")
    private Boolean emailVerified;

    /** Contact phone, collected on the verify page (not at signup). */
    @Column("phone")
    private String phone;

    @Column("phone_verified")
    private Boolean phoneVerified;

    /** e-KYC outcome: true once photo-ID + face + address verification passed.
     * The only durable trace of the verification - evidence is purged. */
    @Column("id_verified")
    private Boolean idVerified;

    @Column("id_verified_at")
    private java.time.LocalDateTime idVerifiedAt;

    public enum UserRole {
        CUSTOMER,
        ADMIN,
        /** Limited admin: can manage products and categories. Cannot manage other staff,
         * discounts, coupons, orders, or settings beyond their own profile. */
        MANAGER,
        VENDOR
    }
}
