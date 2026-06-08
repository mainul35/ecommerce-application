package com.ecommerce.service.kyc;

import reactor.core.publisher.Mono;

import java.nio.file.Path;

/**
 * Strategy interface for document text extraction. Swappable like
 * PaymentGateway: the default is local Tesseract; a cloud OCR (e.g.
 * Textract) can replace it without touching the KYC pipeline.
 */
public interface OcrProvider {

    /** False when the engine cannot run (e.g. missing traineddata). */
    boolean isAvailable();

    /**
     * Extract raw text from an image on disk. Must never block the event
     * loop - implementations dispatch to boundedElastic. Errors surface as
     * an empty Mono; the pipeline treats that as "needs human review".
     */
    Mono<String> extractText(Path image);
}
