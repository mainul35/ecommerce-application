package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("product_reviews")
public class ProductReview extends BaseEntity {

    @Column("product_id")
    private UUID productId;

    @Column("user_id")
    private UUID userId;

    @Column("rating")
    private Short rating;

    @Column("title")
    private String title;

    @Column("body")
    private String body;
}
