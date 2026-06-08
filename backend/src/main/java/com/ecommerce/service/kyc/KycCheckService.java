package com.ecommerce.service.kyc;

import com.ecommerce.config.KycProperties;
import com.ecommerce.model.KycCase;
import com.ecommerce.model.KycDocument;
import com.ecommerce.model.SellerProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The automated verification pipeline. Produces SIGNALS on the case:
 *
 *   id_document_ok      - the ID image OCRs to substantive text
 *   name_match_score    - typed legal name found in the ID text
 *   bill_document_ok    - the electricity bill OCRs to substantive text
 *   address_match_score - typed address found in the bill text
 *   face_verdict        - advisory vision comparison of ID portrait vs selfies
 *
 * Auto-approval policy (user decision): a case is approved WITHOUT human
 * eyes only when every signal clears its threshold. Anything uncertain -
 * including an unavailable OCR/vision engine - falls to the admin queue.
 * The pipeline never auto-REJECTS: negative evidence is for a human to act on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycCheckService {

    /** Minimum characters of OCR output for a document to count as readable. */
    private static final int MIN_DOCUMENT_TEXT = 25;

    private final OcrProvider ocrProvider;
    private final FaceMatchProvider faceMatchProvider;
    private final KycStorageService storageService;
    private final KycProperties props;

    public record CheckOutcome(KycCase kycCase, boolean autoApprovable) {
    }

    /**
     * Run all checks and write the signals onto the given case entity
     * (not persisted here - the caller owns persistence and the decision).
     */
    public Mono<CheckOutcome> runChecks(KycCase kycCase, SellerProfile profile,
                                        List<KycDocument> documents) {
        Map<KycDocument.KycDocType, KycDocument> byType = documents.stream()
                .collect(Collectors.toMap(KycDocument::getDocType, d -> d));

        Path idFront = path(kycCase, byType.get(KycDocument.KycDocType.ID_FRONT));
        Path bill = path(kycCase, byType.get(KycDocument.KycDocType.UTILITY_BILL));
        List<Path> selfies = List.of(
                        KycDocument.KycDocType.SELFIE_FRONT,
                        KycDocument.KycDocType.SELFIE_LEFT,
                        KycDocument.KycDocType.SELFIE_RIGHT).stream()
                .map(byType::get)
                .filter(java.util.Objects::nonNull)
                .map(d -> path(kycCase, d))
                .toList();

        Mono<String> idTextMono = idFront != null
                ? ocrProvider.extractText(idFront).defaultIfEmpty("")
                : Mono.just("");
        Mono<String> billTextMono = bill != null
                ? ocrProvider.extractText(bill).defaultIfEmpty("")
                : Mono.just("");
        Mono<FaceMatchProvider.Result> faceMono = (idFront != null && !selfies.isEmpty())
                ? faceMatchProvider.compare(idFront, selfies)
                : Mono.just(new FaceMatchProvider.Result(KycCase.FaceVerdict.UNKNOWN, "Missing images"));

        return Mono.zip(idTextMono, billTextMono, faceMono)
                .map(t -> evaluate(kycCase, profile, t.getT1(), t.getT2(), t.getT3()));
    }

    private CheckOutcome evaluate(KycCase kycCase, SellerProfile profile,
                                  String idText, String billText,
                                  FaceMatchProvider.Result face) {
        boolean ocrUsable = ocrProvider.isAvailable();

        // Document readability. When OCR is unavailable the signal stays null
        // ("unknown") rather than false - missing tooling is not user fraud.
        Boolean idOk = ocrUsable ? idText.strip().length() >= MIN_DOCUMENT_TEXT : null;
        Boolean billOk = ocrUsable ? billText.strip().length() >= MIN_DOCUMENT_TEXT : null;
        kycCase.setIdDocumentOk(idOk);
        kycCase.setBillDocumentOk(billOk);
        kycCase.setExtractedIdText(truncate(idText));
        kycCase.setExtractedBillText(truncate(billText));

        BigDecimal nameScore = TextMatcher.nameInText(profile.getLegalName(), idText);
        BigDecimal addressScore = TextMatcher.addressInText(typedAddress(profile), billText);
        kycCase.setNameMatchScore(nameScore);
        kycCase.setAddressMatchScore(addressScore);

        kycCase.setFaceVerdict(face.verdict());
        kycCase.setFaceNote(face.note());

        boolean autoApprovable =
                Boolean.TRUE.equals(idOk)
                        && Boolean.TRUE.equals(billOk)
                        && nameScore.compareTo(props.getThresholds().getNameMatch()) >= 0
                        && addressScore.compareTo(props.getThresholds().getAddressMatch()) >= 0
                        && face.verdict() == KycCase.FaceVerdict.MATCH;

        log.info("KYC checks for case {}: idOk={} billOk={} name={} address={} face={} -> {}",
                kycCase.getId(), idOk, billOk, nameScore, addressScore, face.verdict(),
                autoApprovable ? "AUTO-APPROVE" : "HUMAN REVIEW");
        return new CheckOutcome(kycCase, autoApprovable);
    }

    private String typedAddress(SellerProfile profile) {
        StringBuilder sb = new StringBuilder(profile.getAddressLine1());
        if (profile.getAddressLine2() != null) sb.append(' ').append(profile.getAddressLine2());
        sb.append(' ').append(profile.getCity());
        if (profile.getState() != null) sb.append(' ').append(profile.getState());
        if (profile.getPostalCode() != null) sb.append(' ').append(profile.getPostalCode());
        return sb.toString();
    }

    private Path path(KycCase kycCase, KycDocument document) {
        return document == null ? null
                : storageService.resolve(kycCase.getId(), document.getFileName());
    }

    /** Reviewer display only - cap stored OCR extracts. [purged with the docs] */
    private String truncate(String text) {
        if (text == null) return null;
        String stripped = text.strip();
        return stripped.length() <= 4000 ? stripped : stripped.substring(0, 4000);
    }
}
