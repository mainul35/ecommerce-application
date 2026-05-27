package com.ecommerce.service;

import com.ecommerce.dto.ProductReviewCreateRequest;
import com.ecommerce.dto.ProductReviewDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.ProductReview;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final DatabaseClient db;
    private final ProductRepository productRepository;
    private final UserService userService;

    public Flux<ProductReviewDto> findByProduct(UUID productId) {
        String sql = "SELECT r.id, r.product_id, r.rating, r.title, r.body, r.created_at," +
                     " u.first_name, u.last_name" +
                     " FROM product_reviews r" +
                     " JOIN users u ON r.user_id = u.id" +
                     " WHERE r.product_id = :productId" +
                     " ORDER BY r.created_at DESC";
        return db.sql(sql)
                .bind("productId", productId)
                .map((row, meta) -> mapRowToDto(row))
                .all();
    }

    public Mono<Long> countByProduct(UUID productId) {
        return reviewRepository.countByProductId(productId);
    }

    public Mono<ProductReviewDto> create(UUID productId, String userEmail, ProductReviewCreateRequest request) {
        return userService.findUserEntityByEmail(userEmail)
                .flatMap(user -> reviewRepository.existsByProductIdAndUserId(productId, user.getId())
                        .flatMap(alreadyReviewed -> {
                            if (Boolean.TRUE.equals(alreadyReviewed)) {
                                return Mono.error(new BadRequestException("You have already reviewed this product"));
                            }
                            return productRepository.existsById(productId)
                                    .flatMap(productExists -> {
                                        if (Boolean.FALSE.equals(productExists)) {
                                            return Mono.error(new ResourceNotFoundException("Product not found"));
                                        }
                                        ProductReview review = ProductReview.builder()
                                                .productId(productId)
                                                .userId(user.getId())
                                                .rating(request.getRating())
                                                .title(request.getTitle())
                                                .body(request.getBody())
                                                .build();
                                        return reviewRepository.save(review);
                                    });
                        }))
                .flatMap(saved -> {
                    String sql = "SELECT r.id, r.product_id, r.rating, r.title, r.body, r.created_at," +
                                 " u.first_name, u.last_name" +
                                 " FROM product_reviews r JOIN users u ON r.user_id = u.id" +
                                 " WHERE r.id = :id";
                    return db.sql(sql)
                            .bind("id", saved.getId())
                            .map((row, meta) -> mapRowToDto(row))
                            .one();
                });
    }

    private ProductReviewDto mapRowToDto(io.r2dbc.spi.Row row) {
        String firstName = row.get("first_name", String.class);
        String lastName = row.get("last_name", String.class);
        String reviewerName;
        if (firstName == null) {
            reviewerName = "Anonymous";
        } else if (lastName == null || lastName.isEmpty()) {
            reviewerName = firstName;
        } else {
            reviewerName = firstName + " " + lastName.charAt(0) + ".";
        }

        return ProductReviewDto.builder()
                .id(row.get("id", UUID.class))
                .productId(row.get("product_id", UUID.class))
                .reviewerName(reviewerName)
                .rating(row.get("rating", Short.class))
                .title(row.get("title", String.class))
                .body(row.get("body", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .build();
    }
}
