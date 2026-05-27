package com.ecommerce.service.search;

import com.ecommerce.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Builds embedding text from a product's fields and persists the resulting
 * vector to the products.embedding column via R2DBC DatabaseClient.
 *
 * Embedding text format: "<name>. <description>. <flattened attributes>"
 * This multimodal text gives the model the product's semantic profile
 * so customers can find it with natural-language queries in any language.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private final OllamaEmbeddingService ollamaService;
    private final DatabaseClient db;

    /**
     * Generates and stores an embedding for a single product.
     * Errors are swallowed — a missing embedding never fails a product save.
     */
    public Mono<Void> embedProduct(Product product) {
        String text = buildEmbeddingText(product.getName(), product.getDescription(), product.getAttributes());
        return ollamaService.embed(text)
                .flatMap(v -> storeEmbedding(product.getId(), ollamaService.toVectorString(v)))
                .doOnSuccess(v -> log.debug("Embedded product {}", product.getId()))
                .doOnError(e -> log.warn("Embedding skipped for {}: {}", product.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Re-embeds every active product in the catalog.
     * Used by the admin reindex endpoint (POST /api/search/reindex).
     * Runs with concurrency=4 to avoid saturating Ollama.
     */
    public Flux<UUID> embedAll() {
        return db.sql("SELECT id, name, description, attributes FROM products WHERE is_active = true ORDER BY created_at")
                .map(row -> new EmbedTarget(
                        row.get("id", UUID.class),
                        row.get("name", String.class),
                        row.get("description", String.class),
                        row.get("attributes", String.class)))
                .all()
                .flatMap(t -> {
                    String text = buildEmbeddingText(t.name(), t.description(), t.attributes());
                    return ollamaService.embed(text)
                            .flatMap(v -> storeEmbedding(t.id(), ollamaService.toVectorString(v)))
                            .thenReturn(t.id())
                            .doOnError(e -> log.warn("Skipped reindex for {}: {}", t.id(), e.getMessage()))
                            .onErrorResume(e -> Mono.empty());
                }, 4);
    }

    private Mono<Void> storeEmbedding(UUID id, String vectorStr) {
        return db.sql("UPDATE products SET embedding = (:vec)::vector WHERE id = :id")
                .bind("vec", vectorStr)
                .bind("id", id)
                .then();
    }

    String buildEmbeddingText(String name, String description, String attributes) {
        StringBuilder sb = new StringBuilder(name != null ? name : "");
        if (description != null && !description.isBlank()) {
            sb.append(". ").append(description);
        }
        if (attributes != null && attributes.length() > 2) {
            // Flatten {"color":"red","size":"XL"} → "color: red, size: XL"
            String flat = attributes
                    .replaceAll("[{}\"\\[\\]]", "")
                    .replace(":", ": ")
                    .replace(",", ", ");
            sb.append(". ").append(flat);
        }
        return sb.toString();
    }

    private record EmbedTarget(UUID id, String name, String description, String attributes) {}
}
