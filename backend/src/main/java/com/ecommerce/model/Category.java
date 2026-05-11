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
@Table("categories")
public class Category extends BaseEntity {

    @Column("name")
    private String name;

    @Column("slug")
    private String slug;

    @Column("description")
    private String description;

    @Column("parent_id")
    private UUID parentId;

    @Column("image_url")
    private String imageUrl;

    @Column("is_active")
    private Boolean isActive;
}
