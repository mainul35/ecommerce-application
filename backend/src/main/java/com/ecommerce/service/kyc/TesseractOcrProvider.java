package com.ecommerce.service.kyc;

import com.ecommerce.config.KycProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local OCR via Tesseract (tess4j). Needs traineddata files on disk -
 * configure kyc.ocr.tessdata-path and drop eng.traineddata (plus
 * ben.traineddata for Bangla NIDs) there, from
 * https://github.com/tesseract-ocr/tessdata_fast.
 *
 * Tesseract is NOT thread-safe per instance and calls are CPU-bound and
 * blocking, so each extraction creates its own engine on boundedElastic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TesseractOcrProvider implements OcrProvider {

    private final KycProperties props;

    private boolean available;

    @PostConstruct
    void init() {
        String tessdata = props.getOcr().getTessdataPath();
        available = tessdata != null && !tessdata.isBlank()
                && Files.isDirectory(Paths.get(tessdata));
        if (available) {
            log.info("KYC OCR ready: tessdata={} languages={}",
                    tessdata, props.getOcr().getLanguages());
        } else {
            log.warn("KYC OCR unavailable - kyc.ocr.tessdata-path not set or missing. "
                    + "Submitted cases will route to human review.");
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Mono<String> extractText(Path image) {
        if (!available) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    Tesseract tesseract = new Tesseract();
                    tesseract.setDatapath(props.getOcr().getTessdataPath());
                    tesseract.setLanguage(props.getOcr().getLanguages());
                    return tesseract.doOCR(image.toFile());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("OCR failed for {}: {}", image.getFileName(), e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
