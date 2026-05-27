package com.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewCreateRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Short rating;

    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String body;
}
