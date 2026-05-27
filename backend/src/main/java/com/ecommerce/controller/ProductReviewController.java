package com.ecommerce.controller;

import com.ecommerce.dto.ProductReviewCreateRequest;
import com.ecommerce.dto.ProductReviewDto;
import com.ecommerce.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @GetMapping
    public Mono<List<ProductReviewDto>> list(@PathVariable UUID productId) {
        return reviewService.findByProduct(productId).collectList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProductReviewDto> create(
            @PathVariable UUID productId,
            @RequestBody @Validated ProductReviewCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return reviewService.create(productId, userDetails.getUsername(), request);
    }
}
