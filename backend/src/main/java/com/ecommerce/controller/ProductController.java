package com.ecommerce.controller;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ProductDto;
import com.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Public catalog read-only endpoints. Write/admin operations live in
 * {@link com.ecommerce.admin.controller.AdminProductController}.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Public product catalog")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products with pagination")
    public Mono<PagedResponse<ProductDto>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) {

        if (search != null && !search.isEmpty()) {
            return productService.search(search, page, size);
        }
        if (categoryId != null) {
            return productService.findByCategory(categoryId, page, size, sortBy);
        }
        return productService.findAll(page, size, sortBy);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public Mono<ProductDto> getProductById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get products by category")
    public Mono<PagedResponse<ProductDto>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String sortBy) {
        return productService.findByCategory(categoryId, page, size, sortBy);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products")
    public Mono<PagedResponse<ProductDto>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productService.search(q, page, size);
    }
}
