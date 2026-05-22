package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMediaDto {
    private UUID id;
    private UUID productId;
    /** "IMAGE" or "VIDEO" */
    private String mediaType;
    /** Relative URL path, e.g. /uploads/products/{id}/uuid.jpg */
    private String url;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
