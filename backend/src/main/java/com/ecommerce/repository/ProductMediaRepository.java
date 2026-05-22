package com.ecommerce.repository;

import com.ecommerce.model.ProductMedia;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ProductMediaRepository extends ReactiveCrudRepository<ProductMedia, UUID> {

    Flux<ProductMedia> findByProductIdOrderBySortOrderAsc(UUID productId);

    Mono<Long> countByProductId(UUID productId);

    @Modifying
    @Query("UPDATE product_media SET sort_order = :sortOrder WHERE id = :id AND product_id = :productId")
    Mono<Integer> updateSortOrder(@Param("id") UUID id,
                                  @Param("productId") UUID productId,
                                  @Param("sortOrder") int sortOrder);
}
