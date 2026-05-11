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
@Table("order_items")
public class OrderItem extends BaseEntity {

    @Column("order_id")
    private UUID orderId;

    @Column("product_id")
    private UUID productId;

    @Column("product_name")
    private String productName;

    @Column("product_image")
    private String productImage;

    @Column("quantity")
    private Integer quantity;

    @Column("price")
    private BigDecimal price;
}
