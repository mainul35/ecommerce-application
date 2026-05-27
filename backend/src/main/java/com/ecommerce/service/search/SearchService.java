package com.ecommerce.service.search;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ProductDto;
import com.ecommerce.model.Product;
import com.ecommerce.repository.HybridSearchRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final OllamaEmbeddingService embeddingService;
    private final ProductEmbeddingService productEmbeddingService;
    private final HybridSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    /**
     * Hybrid search: embeds the query via Ollama and runs the combined
     * trgm + vector SQL. Falls back to trgm-only when Ollama is unavailable.
     */
    public Mono<PagedResponse<ProductDto>> search(String query, int page, int size, UUID regionId) {
        int offset = page * size;
        return embeddingService.embed(query)
                .map(embeddingService::toVectorString)
                .onErrorResume(e -> {
                    log.info("Ollama unavailable for '{}', using trgm fallback", query);
                    return Mono.empty();
                })
                .<PagedResponse<ProductDto>>flatMap(vec -> runSearch(query, vec, page, size, offset))
                .switchIfEmpty(Mono.defer(() -> runSearch(query, null, page, size, offset)));
    }

    /** Re-embeds every active product. Called from the admin reindex endpoint. */
    public Mono<Long> reindexAll() {
        return productEmbeddingService.embedAll().count();
    }

    private Mono<PagedResponse<ProductDto>> runSearch(String query, String vectorStr, int page, int size, int offset) {
        Mono<List<ProductDto>> itemsMono = searchRepository.searchIds(query, vectorStr, size, offset)
                .collectList()
                .flatMap(ids -> {
                    if (ids.isEmpty()) return Mono.just(List.<Product>of());
                    return productRepository.findAllById(ids)
                            .collectMap(Product::getId)
                            .map(map -> ids.stream()
                                    .map(map::get)
                                    .filter(Objects::nonNull)
                                    .toList());
                })
                .flatMap(products -> productService.enrichAll(products).collectList());

        Mono<Long> countMono = searchRepository.count(query, vectorStr);

        return Mono.zip(itemsMono, countMono)
                .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
    }
}
