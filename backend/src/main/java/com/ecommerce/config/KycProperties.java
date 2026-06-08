package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "kyc")
public class KycProperties {

    /**
     * PRIVATE directory for verification evidence. Deliberately separate from
     * app.upload-dir: nothing here is ever exposed through the public
     * /uploads/** resource mapping - access goes through party-checked
     * streaming endpoints only.
     */
    private String storageDir = "kyc-private";

    /** Hard retention cap for verification evidence, in hours. */
    private long retentionHours = 72;

    /** How often the purge job sweeps for expired evidence (ms). */
    private long purgeIntervalMs = 600_000; // 10 minutes

    private final Ocr ocr = new Ocr();
    private final Vision vision = new Vision();
    private final Thresholds thresholds = new Thresholds();

    @Getter
    @Setter
    public static class Ocr {
        /**
         * Directory containing Tesseract traineddata files (eng.traineddata etc.).
         * When unset or missing, OCR is reported unavailable and submitted cases
         * route to the human review queue instead of failing.
         */
        private String tessdataPath = "";
        /** Languages passed to Tesseract, e.g. "eng" or "eng+ben" for BD NIDs. */
        private String languages = "eng";
    }

    @Getter
    @Setter
    public static class Vision {
        /** Ollama vision model used for the advisory face comparison. */
        private String model = "llava";
        /** Per-call timeout - vision inference is slow on CPU. */
        private long timeoutSeconds = 120;
    }

    @Getter
    @Setter
    public static class Thresholds {
        /** Minimum 0..1 name similarity for auto-approval. */
        private BigDecimal nameMatch = new BigDecimal("0.80");
        /** Minimum 0..1 address similarity for auto-approval. */
        private BigDecimal addressMatch = new BigDecimal("0.70");
    }
}
