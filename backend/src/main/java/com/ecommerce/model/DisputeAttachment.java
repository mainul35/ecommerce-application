package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Image/video evidence attached to a dispute message. Files live under uploads/disputes/{disputeId}/. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("dispute_attachments")
public class DisputeAttachment extends BaseEntity {

    @Column("dispute_message_id")
    private UUID disputeMessageId;

    /** Stored (randomized) file name on disk. */
    @Column("file_name")
    private String fileName;

    @Column("original_name")
    private String originalName;

    /** Public-path URL, e.g. /uploads/disputes/{disputeId}/{fileName}. */
    @Column("url")
    private String url;

    @Column("content_type")
    private String contentType;

    @Column("attachment_type")
    private AttachmentType attachmentType;

    @Column("size_bytes")
    private Long sizeBytes;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum AttachmentType implements NumericEnum {
        IMAGE(0),
        VIDEO(1);

        private final int code;
    }
}
