package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Geographic region (country, in this implementation). Maps a country to
 * its default currency. The IP-based geolocator on the storefront looks up
 * a Region by country_code to discover the customer's default currency
 * and the set of products available to them.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("regions")
public class Region extends BaseEntity {

    @Column("name")
    private String name;

    /** ISO 3166-1 alpha-2 country code, e.g. "US", "DE", "JP". */
    @Column("country_code")
    private String countryCode;

    /** Default currency for this region (FK to currencies.code). */
    @Column("currency_code")
    private String currencyCode;

    @Column("is_active")
    private Boolean isActive;
}
