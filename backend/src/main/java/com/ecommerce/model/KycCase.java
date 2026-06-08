package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One e-KYC verification attempt. Holds the automated check signals
 * (scores contain no raw PII and are kept); the OCR text extracts and
 * face note are derived from documents and are NULLED when the documents
 * purge - on decision or at the 72h retention deadline, whichever first.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("kyc_cases")
public class KycCase extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    @Column("status")
    private KycStatus status;

    /** 0..1 similarity between the typed legal name and the OCR'd ID text. */
    @Column("name_match_score")
    private BigDecimal nameMatchScore;

    /** 0..1 similarity between the typed address and the OCR'd utility bill. */
    @Column("address_match_score")
    private BigDecimal addressMatchScore;

    @Column("face_verdict")
    private FaceVerdict faceVerdict;

    @Column("id_document_ok")
    private Boolean idDocumentOk;

    @Column("bill_document_ok")
    private Boolean billDocumentOk;

    /** OCR extract of the photo ID, for reviewer display. [purged] */
    @Column("extracted_id_text")
    private String extractedIdText;

    /** OCR extract of the utility bill, for reviewer display. [purged] */
    @Column("extracted_bill_text")
    private String extractedBillText;

    /** Vision model's reasoning about the face comparison. [purged] */
    @Column("face_note")
    private String faceNote;

    @Column("submitted_at")
    private LocalDateTime submittedAt;

    /** Hard retention deadline for the evidence: submitted_at + 72h. */
    @Column("expires_at")
    private LocalDateTime expiresAt;

    /** True when the verdict came from the automated pipeline, not a human. */
    @Column("auto_decided")
    private Boolean autoDecided;

    @Column("decided_by_user_id")
    private UUID decidedByUserId;

    @Column("decided_at")
    private LocalDateTime decidedAt;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("documents_purged_at")
    private LocalDateTime documentsPurgedAt;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum KycStatus implements NumericEnum {
        /** Collecting documents; nothing submitted yet. */
        DRAFT(0),
        /** Submitted, automated checks queued. */
        SUBMITTED(1),
        /** Automated checks running. */
        CHECKING(2),
        /** Automation couldn't fully clear it - waiting for a human. */
        IN_REVIEW(3),
        /** Verified - users.id_verified flipped true. Terminal. */
        APPROVED(4),
        /** Rejected - the user may start a fresh case. Terminal. */
        REJECTED(5),
        /** Evidence hit the 72h retention deadline undecided. Terminal. */
        EXPIRED(6);

        private final int code;
    }

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum FaceVerdict implements NumericEnum {
        /** No verdict (vision engine unavailable or not yet run). */
        UNKNOWN(0),
        MATCH(1),
        NO_MATCH(2);

        private final int code;
    }
}
