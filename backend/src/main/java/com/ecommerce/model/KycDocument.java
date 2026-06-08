package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Transient verification evidence: photo-ID sides, guided selfies, and the
 * utility bill. Files live in the PRIVATE kyc storage dir (never under the
 * public /uploads mapping) and are deleted - row and file - when the case
 * is decided or hits the 72h retention deadline.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("kyc_documents")
public class KycDocument extends BaseEntity {

    @Column("case_id")
    private UUID caseId;

    @Column("doc_type")
    private KycDocType docType;

    /** Randomized stored file name within the case's private directory. */
    @Column("file_name")
    private String fileName;

    @Column("content_type")
    private String contentType;

    @Column("size_bytes")
    private Long sizeBytes;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum KycDocType implements NumericEnum {
        ID_FRONT(0),
        ID_BACK(1),
        SELFIE_FRONT(2),
        SELFIE_LEFT(3),
        SELFIE_RIGHT(4),
        UTILITY_BILL(5);

        private final int code;
    }
}
