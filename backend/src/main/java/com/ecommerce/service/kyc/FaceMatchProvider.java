package com.ecommerce.service.kyc;

import com.ecommerce.model.KycCase;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for comparing the photo-ID portrait against the
 * captured selfies. The default Ollama-vision implementation is ADVISORY
 * (a vision LLM is not a biometric system); a proper face-embedding or
 * cloud provider can replace it without touching the KYC pipeline.
 */
public interface FaceMatchProvider {

    /** False when the engine cannot run (e.g. Ollama down / model missing). */
    boolean isAvailable();

    /**
     * Compare the face on the ID document with the selfies.
     * UNKNOWN verdicts route the case to human review.
     */
    Mono<Result> compare(Path idFront, List<Path> selfies);

    record Result(KycCase.FaceVerdict verdict, String note) {
    }
}
