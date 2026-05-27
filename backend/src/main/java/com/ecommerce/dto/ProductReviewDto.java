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
public class ProductReviewDto {
    private UUID id;
    private UUID productId;
    private String reviewerName;
    private Short rating;
    private String title;
    private String body;
    private LocalDateTime createdAt;
}
