package com.ecommerce.admin.controller;

import com.ecommerce.dto.DiscountDto;
import com.ecommerce.model.Discount;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.DiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin discount management. Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} - this controller has no role logic.
 */
@RestController
@RequestMapping("/api/admin/discounts")
@RequiredArgsConstructor
@Tag(name = "Admin - Discounts", description = "Discount campaign management")
public class AdminDiscountController {

    private final DiscountService discountService;
    private final ProductRepository productRepository;

    @GetMapping
    @Operation(summary = "List discounts (newest first). Optionally filter by scope + scopeTargetId.")
    public Flux<DiscountDto> list(
            @RequestParam(required = false) Discount.DiscountScope scope,
            @RequestParam(required = false) UUID scopeTargetId) {
        if (scope != null && scopeTargetId != null) {
            return discountService.listForScopeTarget(scope, scopeTargetId);
        }
        return discountService.listAll();
    }

    @GetMapping("/applicable-to-product/{productId}")
    @Operation(summary = "List all active discounts that apply to this product right now "
            + "(PRODUCT-scope on the product, CATEGORY-scope on its category, plus SITEWIDE)")
    public Flux<DiscountDto> applicableToProduct(@PathVariable UUID productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found")))
                .flatMapMany(discountService::findApplicableToProduct);
    }

    @GetMapping("/applicable-to-category/{categoryId}")
    @Operation(summary = "List all active discounts that apply to products in this category "
            + "(CATEGORY-scope on the category, plus SITEWIDE)")
    public Flux<DiscountDto> applicableToCategory(@PathVariable UUID categoryId) {
        return discountService.findApplicableToCategory(categoryId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a discount by ID")
    public Mono<DiscountDto> get(@PathVariable UUID id) {
        return discountService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a discount")
    public Mono<DiscountDto> create(@Valid @RequestBody DiscountDto dto) {
        return discountService.create(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a discount")
    public Mono<DiscountDto> update(@PathVariable UUID id, @Valid @RequestBody DiscountDto dto) {
        return discountService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a discount permanently")
    public Mono<Void> delete(@PathVariable UUID id) {
        return discountService.delete(id);
    }
}
