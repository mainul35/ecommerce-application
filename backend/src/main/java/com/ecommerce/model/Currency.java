package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ISO 4217 currency definition. Primary key is the 3-letter code (USD, EUR,
 * JPY...) so it doesn't extend {@link BaseEntity} (which assumes a UUID PK).
 *
 * exchange_rate is the multiplier from base: 1 unit of base currency equals
 * exchange_rate units of this currency. The base row carries 1.0; at most
 * one row may have is_base=true (enforced by uq_currencies_one_base partial
 * unique index).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("currencies")
public class Currency implements Persistable<String> {

    @Id
    @Column("code")
    private String code;

    @Column("name")
    private String name;

    @Column("symbol")
    private String symbol;

    @Column("exchange_rate")
    private BigDecimal exchangeRate;

    @Column("is_base")
    private Boolean isBase;

    @Column("is_active")
    private Boolean isActive;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String getId() {
        return code;
    }

    /** Same convention as BaseEntity: createdAt-null means new (INSERT). */
    @Override
    @Transient
    public boolean isNew() {
        return createdAt == null;
    }
}
