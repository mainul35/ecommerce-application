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

    public enum UserRole {
        CUSTOMER,
        ADMIN,
        /** Limited admin: can manage products and categories. Cannot manage other staff,
         * discounts, coupons, orders, or settings beyond their own profile. */
        MANAGER,
        VENDOR
    }
}
