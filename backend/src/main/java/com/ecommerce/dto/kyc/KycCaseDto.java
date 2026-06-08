package com.ecommerce.dto.kyc;

import com.ecommerce.model.KycCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Case view. The OCR extracts / face note ride along for the ADMIN review
 * surface and are null for everyone once the evidence purges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycCaseDto {
    private UUID id;
    private UUID userId;
    private KycCase.KycStatus status;
    private BigDecimal nameMatchScore;
    private BigDecimal addressMatchScore;
    private KycCase.FaceVerdict faceVerdict;
    private Boolean idDocumentOk;
    private Boolean billDocumentOk;
    private String extractedIdText;
    private String extractedBillText;
    private String faceNote;
    private LocalDateTime submittedAt;
    private LocalDateTime expiresAt;
    private Boolean autoDecided;
    private LocalDateTime decidedAt;
    private String rejectionReason;
    private LocalDateTime documentsPurgedAt;
    private List<KycDocumentDto> documents;
    private LocalDateTime createdAt;

    /** Strip reviewer-only fields for the case owner's own status view. */
    public KycCaseDto forOwner() {
        this.extractedIdText = null;
        this.extractedBillText = null;
        this.faceNote = null;
        return this;
    }
}
