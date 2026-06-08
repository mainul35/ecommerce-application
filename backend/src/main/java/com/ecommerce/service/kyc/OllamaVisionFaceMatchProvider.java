package com.ecommerce.service.kyc;

import com.ecommerce.config.KycProperties;
import com.ecommerce.model.KycCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ADVISORY face comparison via a local Ollama vision model (default: llava).
 * A vision LLM is not a biometric system - its verdict is one auto-approval
 * signal among several, and anything other than a confident MATCH routes the
 * case to human review. Reuses the same Ollama server as semantic search;
 * pull the model with: ollama pull llava
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaVisionFaceMatchProvider implements FaceMatchProvider {

    private static final String PROMPT = """
            You are assisting an identity verification reviewer.
            The FIRST image is the portrait on a government photo ID.
            The remaining images are live selfies of the person from different angles.
            Question: do the selfies show the SAME person as the ID portrait?
            Answer on the first line with exactly one word: MATCH, NO_MATCH, or UNSURE.
            On the second line give a one-sentence reason.
            Be conservative: if image quality prevents a confident judgement, answer UNSURE.
            """;

    private final WebClient ollamaWebClient;
    private final KycProperties props;

    /**
     * Availability is probed per run rather than cached: Ollama may be
     * started or the model pulled while the app is running.
     */
    @Override
    public boolean isAvailable() {
        return true; // resolved at call time; failures yield UNKNOWN verdicts
    }

    @Override
    public Mono<Result> compare(Path idFront, List<Path> selfies) {
        return Mono.fromCallable(() -> {
                    List<String> images = new ArrayList<>();
                    images.add(Base64.getEncoder().encodeToString(Files.readAllBytes(idFront)));
                    for (Path selfie : selfies) {
                        images.add(Base64.getEncoder().encodeToString(Files.readAllBytes(selfie)));
                    }
                    return images;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(images -> ollamaWebClient.post()
                        .uri("/api/chat")
                        .bodyValue(Map.of(
                                "model", props.getVision().getModel(),
                                "stream", false,
                                "messages", List.of(Map.of(
                                        "role", "user",
                                        "content", PROMPT,
                                        "images", images))))
                        .retrieve()
                        .bodyToMono(ChatResponse.class)
                        .timeout(Duration.ofSeconds(props.getVision().getTimeoutSeconds())))
                .map(response -> parse(response.message() != null ? response.message().content() : ""))
                .doOnError(e -> log.warn("Vision face match unavailable ({}): {}",
                        props.getVision().getModel(), e.getMessage()))
                .onErrorResume(e -> Mono.just(new Result(KycCase.FaceVerdict.UNKNOWN,
                        "Vision engine unavailable - human review required")));
    }

    private Result parse(String content) {
        String text = content == null ? "" : content.strip();
        String firstLine = text.lines().findFirst().orElse("").toUpperCase(Locale.ROOT);
        String note = text.lines().skip(1).findFirst().orElse(firstLine).strip();

        // NO_MATCH must be checked before MATCH ("NO_MATCH" contains "MATCH").
        KycCase.FaceVerdict verdict;
        if (firstLine.contains("NO_MATCH") || firstLine.contains("NO MATCH")) {
            verdict = KycCase.FaceVerdict.NO_MATCH;
        } else if (firstLine.contains("MATCH")) {
            verdict = KycCase.FaceVerdict.MATCH;
        } else {
            verdict = KycCase.FaceVerdict.UNKNOWN;
        }
        return new Result(verdict, note.isBlank() ? text : note);
    }

    private record ChatResponse(ChatMessage message) {
        private record ChatMessage(String role, String content) {
        }
    }
}
