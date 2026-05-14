package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private List<String> images;
    private CategoryDto category;
    private Map<String, Object> attributes;
    private Integer stock;
    private String sku;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** When a discount applies, the price after the best applicable discount. Null otherwise. */
    private BigDecimal discountedPrice;
    /** Equivalent percentage off (always populated when discountedPrice is present, even for FIXED discounts). */
    private BigDecimal discountPercent;
    /** Display name of the applied discount, e.g. "Black Friday Sale". */
    private String discountName;
    /** When the applied discount expires (used for countdown display). */
    private LocalDateTime discountEndsAt;

    /**
     * Region restriction. Empty/null means the product is available globally.
     * Populated only on admin reads (the storefront filters by region instead
     * of disclosing the allowed-region set).
     */
    private List<UUID> regionIds;
}
