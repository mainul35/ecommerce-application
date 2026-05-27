package com.ecommerce.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Executes hybrid search queries combining:
 *   - pg_trgm similarity: fuzzy keyword matching on name/description
 *   - pgvector cosine distance: semantic similarity via Ollama embeddings
 *
 * Returns product IDs ordered by a weighted combined score so callers can
 * fetch full Product rows via the standard repository and preserve the ranking.
 *
 * Falls back to trgm-only SQL when vectorStr is null (Ollama unavailable or
 * products not yet indexed).
 *
 * Score weights: 35% vector + 65% trgm (tunable).
 * Vector threshold: cosine distance < 0.55 (similarity > 0.45).
 * ILIKE fallback in WHERE ensures short queries (< 3 chars) still match.
 */
@Repository
@RequiredArgsConstructor
public class HybridSearchRepository {

    private final DatabaseClient db;

    // ── Hybrid (trgm + vector) ────────────────────────────────────────────────

    private static final String HYBRID_IDS = """
            SELECT id FROM (
                SELECT p.id,
                       GREATEST(
                           similarity(p.name, :q),
                           similarity(COALESCE(p.description, ''), :q)
                       ) AS trgm_score,
                       CASE WHEN p.embedding IS NOT NULL
                            THEN 1.0 - (p.embedding <=> (:vec)::vector)
                            ELSE 0.0 END AS vector_score
                FROM products p
                WHERE p.is_active = true
                  AND (
                      p.name % :q
                      OR COALESCE(p.description, '') % :q
                      OR p.name ILIKE '%' || :q || '%'
                      OR (p.embedding IS NOT NULL AND (p.embedding <=> (:vec)::vector) < 0.55)
                  )
            ) scored
            ORDER BY 0.35 * vector_score + 0.65 * trgm_score DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String HYBRID_COUNT = """
            SELECT COUNT(*) FROM products p
            WHERE p.is_active = true
              AND (
                  p.name % :q
                  OR COALESCE(p.description, '') % :q
                  OR p.name ILIKE '%' || :q || '%'
                  OR (p.embedding IS NOT NULL AND (p.embedding <=> (:vec)::vector) < 0.55)
              )
            """;

    // ── Trgm-only fallback (no Ollama) ────────────────────────────────────────

    private static final String TRGM_IDS = """
            SELECT id FROM (
                SELECT p.id,
                       GREATEST(
                           similarity(p.name, :q),
                           similarity(COALESCE(p.description, ''), :q)
                       ) AS trgm_score
                FROM products p
                WHERE p.is_active = true
                  AND (
                      p.name % :q
                      OR COALESCE(p.description, '') % :q
                      OR p.name ILIKE '%' || :q || '%'
                  )
            ) scored
            ORDER BY trgm_score DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String TRGM_COUNT = """
            SELECT COUNT(*) FROM products p
            WHERE p.is_active = true
              AND (
                  p.name % :q
                  OR COALESCE(p.description, '') % :q
                  OR p.name ILIKE '%' || :q || '%'
              )
            """;

    /** Returns product IDs in descending relevance order. */
    public Flux<UUID> searchIds(String query, String vectorStr, int limit, int offset) {
        if (vectorStr != null) {
            return db.sql(HYBRID_IDS)
                    .bind("q", query).bind("vec", vectorStr)
                    .bind("limit", limit).bind("offset", offset)
                    .map(row -> row.get("id", UUID.class))
                    .all();
        }
        return db.sql(TRGM_IDS)
                .bind("q", query)
                .bind("limit", limit).bind("offset", offset)
                .map(row -> row.get("id", UUID.class))
                .all();
    }

    /** Total match count for pagination. */
    public Mono<Long> count(String query, String vectorStr) {
        if (vectorStr != null) {
            return db.sql(HYBRID_COUNT)
                    .bind("q", query).bind("vec", vectorStr)
                    .map(row -> row.get(0, Long.class))
                    .one();
        }
        return db.sql(TRGM_COUNT)
                .bind("q", query)
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
