package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sale price campaign. Resolves to a price reduction on matching products
 * via {@link com.ecommerce.service.DiscountService#bestFor}.
 *
 * Scope semantics:
 *   PRODUCT   - {@code scopeTargetId} is a product id (just that product)
 *   CATEGORY  - {@code scopeTargetId} is a category id (every product in it)
 *   SITEWIDE  - {@code scopeTargetId} is null (every active product)
 *
 * Window: a discount is "live" when {@code isActive=true}, {@code startsAt}
 * is null or in the past, and {@code endsAt} is null or in the future.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("discounts")
public class Discount extends BaseEntity {

    @Column("name")
    private String name;

    @Column("type")
    private DiscountType type;

    @Column("value")
    private BigDecimal value;

    @Column("scope")
    private DiscountScope scope;

    @Column("scope_target_id")
    private UUID scopeTargetId;

    @Column("starts_at")
    private LocalDateTime startsAt;

    @Column("ends_at")
    private LocalDateTime endsAt;

    @Column("is_active")
    private Boolean isActive;

    public enum DiscountType { PERCENTAGE, FIXED }

    public enum DiscountScope { PRODUCT, CATEGORY, SITEWIDE }
}
