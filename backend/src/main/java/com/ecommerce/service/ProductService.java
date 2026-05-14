package com.ecommerce.service;

import com.ecommerce.model.Discount;
import com.ecommerce.model.Product;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ProductCreateRequest;
import com.ecommerce.dto.ProductDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String NOT_FOUND = "Product not found";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final DiscountService discountService;

    /**
     * Storefront product list. When {@code regionId} is non-null, products
     * with region restrictions excluding that region are filtered out.
     * When null, no region filter is applied (admin path / unfiltered list).
     */
    public Mono<PagedResponse<ProductDto>> findAll(int page, int size, String sortBy, UUID regionId) {
        if (regionId != null) {
            int offset = page * size;
            return discountService.findActive().collectList().flatMap(active ->
                    productRepository.findVisibleInRegion(regionId, null, size, offset, sortBy)
                            .flatMap(p -> enrich(p, active))
                            .collectList()
                            .zipWith(productRepository.countVisibleInRegion(regionId, null))
                            .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
        }
        Sort sort = parseSort(sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        return discountService.findActive().collectList().flatMap(active ->
                productRepository.findByIsActiveTrue(pageRequest)
                        .flatMap(p -> enrich(p, active))
                        .collectList()
                        .zipWith(productRepository.countByIsActiveTrue())
                        .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
    }

    public Mono<PagedResponse<ProductDto>> findByCategory(UUID categoryId, int page, int size,
                                                            String sortBy, UUID regionId) {
        if (regionId != null) {
            int offset = page * size;
            return discountService.findActive().collectList().flatMap(active ->
                    productRepository.findVisibleInRegion(regionId, categoryId, size, offset, sortBy)
                            .flatMap(p -> enrich(p, active))
                            .collectList()
                            .zipWith(productRepository.countVisibleInRegion(regionId, categoryId))
                            .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
        }
        Sort sort = parseSort(sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        return discountService.findActive().collectList().flatMap(active ->
                productRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageRequest)
                        .flatMap(p -> enrich(p, active))
                        .collectList()
                        .zipWith(productRepository.countByCategoryIdAndIsActiveTrue(categoryId))
                        .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
    }

    public Mono<PagedResponse<ProductDto>> search(String query, int page, int size, UUID regionId) {
        int offset = page * size;
        if (regionId != null) {
            return discountService.findActive().collectList().flatMap(active ->
                    productRepository.searchVisibleInRegion(query, regionId, size, offset)
                            .flatMap(p -> enrich(p, active))
                            .collectList()
                            .zipWith(productRepository.countSearchVisibleInRegion(query, regionId))
                            .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
        }
        return discountService.findActive().collectList().flatMap(active ->
                productRepository.searchByName(query, size, offset)
                        .flatMap(p -> enrich(p, active))
                        .collectList()
                        .zipWith(productRepository.countBySearch(query))
                        .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
    }

    /**
     * Storefront product detail. When {@code regionId} is non-null and the
     * product is region-restricted to other regions, returns 404 - we don't
     * surface "exists but blocked" to the customer.
     */
    public Mono<ProductDto> findById(UUID id, UUID regionId) {
        Mono<Product> base = productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .filter(Product::getIsActive)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)));

        if (regionId != null) {
            base = base.flatMap(p -> productRepository.isProductVisibleInRegion(p.getId(), regionId)
                    .flatMap(visible -> Boolean.TRUE.equals(visible)
                            ? Mono.just(p)
                            : Mono.error(new ResourceNotFoundException(NOT_FOUND))));
        }
        return base.flatMap(p -> discountService.findActive().collectList()
                .flatMap(active -> enrich(p, active)));
    }

    /** Admin read - includes regionIds, ignores region filter. */
    public Mono<ProductDto> findByIdForAdmin(UUID id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(p -> discountService.findActive().collectList()
                        .flatMap(active -> enrich(p, active))
                        .flatMap(dto -> productRepository.findRegionIdsForProduct(p.getId())
                                .collectList()
                                .map(ids -> {
                                    dto.setRegionIds(ids);
                                    return dto;
                                })));
    }

    @Transactional
    public Mono<ProductDto> create(ProductCreateRequest request) {
        return productRepository.existsBySku(request.getSku())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BadRequestException("SKU already exists"));
                    }
                    return categoryRepository.findById(request.getCategoryId())
                            .switchIfEmpty(Mono.error(new BadRequestException("Category not found")));
                })
                .flatMap(category -> {
                    Product product = productMapper.toEntity(request);
                    product.setId(UUID.randomUUID());
                    return productRepository.save(product);
                })
                .flatMap(saved -> productRepository
                        .replaceProductRegions(saved.getId(), nonNull(request.getRegionIds()))
                        .thenReturn(saved))
                .flatMap(p -> enrich(p, List.of())
                        .map(dto -> { dto.setRegionIds(nonNull(request.getRegionIds())); return dto; }));
    }

    @Transactional
    public Mono<ProductDto> update(UUID id, ProductCreateRequest request) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> {
                    existing.setName(request.getName());
                    existing.setDescription(request.getDescription());
                    existing.setPrice(request.getPrice());
                    existing.setImageUrl(request.getImageUrl());
                    existing.setCategoryId(request.getCategoryId());
                    existing.setStock(request.getStock());
                    return productRepository.save(existing);
                })
                .flatMap(saved -> productRepository
                        .replaceProductRegions(saved.getId(), nonNull(request.getRegionIds()))
                        .thenReturn(saved))
                .flatMap(p -> enrich(p, List.of())
                        .map(dto -> { dto.setRegionIds(nonNull(request.getRegionIds())); return dto; }));
    }

    public Mono<Void> delete(UUID id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(product -> {
                    product.setIsActive(false);
                    return productRepository.save(product);
                })
                .then();
    }

    /**
     * Build a ProductDto from a Product, attaching the resolved category and
     * (if any) the best applicable active discount. Pass an empty list to skip
     * discount resolution (e.g. for admin write paths that don't need it).
     */
    private Mono<ProductDto> enrich(Product product, List<Discount> activeDiscounts) {
        return categoryRepository.findById(product.getCategoryId())
                .map(category -> productMapper.toDto(product, categoryMapper.toDto(category)))
                .defaultIfEmpty(productMapper.toDto(product))
                .map(dto -> applyDiscount(dto, product, activeDiscounts));
    }

    private ProductDto applyDiscount(ProductDto dto, Product product, List<Discount> activeDiscounts) {
        if (activeDiscounts.isEmpty()) return dto;
        discountService.bestFor(product, activeDiscounts).ifPresent(best -> {
            dto.setDiscountedPrice(best.getDiscountedPrice());
            dto.setDiscountPercent(best.getPercent());
            dto.setDiscountName(best.getName());
            dto.setDiscountEndsAt(best.getEndsAt());
        });
        return dto;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private Sort parseSort(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return switch (sortBy) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "name_asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name_desc" -> Sort.by(Sort.Direction.DESC, "name");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
