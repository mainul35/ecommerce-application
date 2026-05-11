package com.ecommerce.controller;

import com.ecommerce.dto.CategoryDto;
import com.ecommerce.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Public category read-only endpoints. Write/admin operations live in
 * {@link com.ecommerce.admin.controller.AdminCategoryController}.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Public category browsing")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories")
    public Flux<CategoryDto> getAllCategories() {
        return categoryService.findAll();
    }

    @GetMapping("/root")
    @Operation(summary = "Get root categories (no parent)")
    public Flux<CategoryDto> getRootCategories() {
        return categoryService.findRootCategories();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    public Mono<CategoryDto> getCategoryById(@PathVariable UUID id) {
        return categoryService.findById(id);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get category by slug")
    public Mono<CategoryDto> getCategoryBySlug(@PathVariable String slug) {
        return categoryService.findBySlug(slug);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get child categories")
    public Flux<CategoryDto> getChildCategories(@PathVariable UUID id) {
        return categoryService.findByParentId(id);
    }
}
