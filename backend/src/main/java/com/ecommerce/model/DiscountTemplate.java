package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * Reusable blueprint for a {@link Discount}. Not a live discount itself -
 * the admin instantiates a template (with target scope and time window)
 * to create an actual {@link Discount} via
 * {@link com.ecommerce.service.DiscountTemplateService#applyToScope}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("discount_templates")
public class DiscountTemplate extends BaseEntity {

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("type")
    private Discount.DiscountType type;

    @Column("value")
    private BigDecimal value;

    /** Optional. When set, applying the template pre-fills ends_at = now + N days. */
    @Column("default_duration_days")
    private Integer defaultDurationDays;
}
