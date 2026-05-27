package com.ecommerce.service.search;

import com.ecommerce.config.OllamaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Calls the local Ollama server to generate embedding vectors.
 * Uses the /api/embed endpoint (Ollama 0.1.26+).
 * Gracefully degrades: on error, callers receive an empty Mono and fall back to trgm-only search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaEmbeddingService {

    private final WebClient ollamaWebClient;
    private final OllamaProperties props;

    /** Embeds {@code text} and returns the raw float vector. Empty on any error. */
    public Mono<float[]> embed(String text) {
        return ollamaWebClient.post()
                .uri("/api/embed")
                .bodyValue(Map.of("model", props.getModel(), "input", text))
                .retrieve()
                .bodyToMono(OllamaEmbedResponse.class)
                .map(r -> toFloatArray(r.embeddings().get(0)))
                .doOnError(e -> log.warn("Ollama embed failed ({}): {}", props.getModel(), e.getMessage()));
    }

    /** Formats a float[] as a pgvector literal: [0.1,0.2,...] */
    String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }

    private float[] toFloatArray(List<Double> values) {
        float[] arr = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i).floatValue();
        }
        return arr;
    }

    private record OllamaEmbedResponse(List<List<Double>> embeddings) {}
}
