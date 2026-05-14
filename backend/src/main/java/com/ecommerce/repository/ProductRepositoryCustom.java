package com.ecommerce.repository;

import com.ecommerce.model.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductRepositoryCustom {

    Flux<Product> searchByName(String search, int limit, long offset);

    Mono<Long> countBySearch(String search);

    Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, int limit, long offset);

    /**
     * Region-restricted listing. A product is visible in a region if it has
     * NO product_regions entries (open / global) OR an entry pointing to the
     * given region. {@code categoryId} is optional - null means "any category".
     */
    Flux<Product> findVisibleInRegion(UUID regionId, UUID categoryId,
                                       int limit, long offset, String orderBy);

    Mono<Long> countVisibleInRegion(UUID regionId, UUID categoryId);

    Flux<Product> searchVisibleInRegion(String search, UUID regionId,
                                         int limit, long offset);

    Mono<Long> countSearchVisibleInRegion(String search, UUID regionId);

    Mono<Boolean> isProductVisibleInRegion(UUID productId, UUID regionId);

    /** Replace the region links for a product. Empty list means "global / no restriction". */
    Mono<Void> replaceProductRegions(UUID productId, List<UUID> regionIds);

    Flux<UUID> findRegionIdsForProduct(UUID productId);
}
