package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("products")
public class Product extends BaseEntity {

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("price")
    private BigDecimal price;

    @Column("original_price")
    private BigDecimal originalPrice;

    @Column("image_url")
    private String imageUrl;

    @Column("images")
    private String images;

    @Column("category_id")
    private UUID categoryId;

    @Column("attributes")
    private String attributes;

    @Column("stock")
    private Integer stock;

    @Column("sku")
    private String sku;

    @Column("is_active")
    private Boolean isActive;

    @Column("vendor_id")
    private UUID vendorId;
}
