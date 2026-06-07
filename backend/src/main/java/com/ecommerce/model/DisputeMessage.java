package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** One message in a dispute conversation thread. May carry image/video attachments. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("dispute_messages")
public class DisputeMessage extends BaseEntity {

    @Column("dispute_id")
    private UUID disputeId;

    @Column("sender_user_id")
    private UUID senderUserId;

    /** Which side the sender was on AT SEND TIME - snapshot so the UI renders sides without joins. */
    @Column("author_role")
    private AuthorRole authorRole;

    @Column("body")
    private String body;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum AuthorRole implements NumericEnum {
        BUYER(0),
        SELLER(1),
        /** Admin / support staff. */
        STAFF(2);

        private final int code;
    }
}
