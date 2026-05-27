package com.ecommerce.controller;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ProductDto;
import com.ecommerce.service.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Hybrid fuzzy + semantic product search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search products — pg_trgm fuzzy matching + pgvector semantic similarity via Ollama")
    public Mono<PagedResponse<ProductDto>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID regionId) {
        return searchService.search(q.trim(), page, Math.min(size, 50), regionId);
    }

    @PostMapping("/reindex")
    @Operation(summary = "Regenerate embeddings for all active products (admin only)")
    public Mono<Map<String, Object>> reindex() {
        return searchService.reindexAll()
                .map(count -> Map.of("reindexed", count, "status", "complete"));
    }
}
