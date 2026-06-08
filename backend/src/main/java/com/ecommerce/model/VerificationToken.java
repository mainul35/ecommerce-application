package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single verification challenge for a contact channel. EMAIL tokens are
 * long opaque strings embedded in a link; PHONE tokens are short numeric
 * OTPs. A token is spent when {@link #consumedAt} is set.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("verification_tokens")
public class VerificationToken extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    @Column("channel")
    private VerificationChannel channel;

    /** EMAIL: opaque link token. PHONE: short numeric OTP. */
    @Column("secret")
    private String secret;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("consumed_at")
    private LocalDateTime consumedAt;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum VerificationChannel implements NumericEnum {
        EMAIL(0),
        PHONE(1);

        private final int code;
    }
}
