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
@Table("product_media")
public class ProductMedia extends BaseEntity {

    @Column("product_id")
    private UUID productId;

    @Column("file_name")
    private String fileName;

    @Column("original_name")
    private String originalName;

    /** "IMAGE" or "VIDEO" */
    @Column("media_type")
    private String mediaType;

    /** Relative URL path served by the backend, e.g. /uploads/products/{id}/uuid.jpg */
    @Column("url")
    private String url;

    @Column("content_type")
    private String contentType;

    @Column("size_bytes")
    private Long sizeBytes;

    @Column("sort_order")
    private Integer sortOrder;
}
