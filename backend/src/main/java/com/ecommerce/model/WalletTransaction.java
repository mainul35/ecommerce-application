package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable ledger entry for every wallet balance change. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("wallet_transactions")
public class WalletTransaction extends BaseEntity {

    @Column("wallet_id")
    private UUID walletId;

    @Column("type")
    private TransactionType type;

    /** Always positive; direction is given by {@link #type}. */
    @Column("amount")
    private BigDecimal amount;

    /** Wallet balance immediately after this entry was applied. */
    @Column("balance_after")
    private BigDecimal balanceAfter;

    @Column("reference_type")
    private ReferenceType referenceType;

    /** Id of the row that caused this entry (escrow tx, return request, dispute...). */
    @Column("reference_id")
    private UUID referenceId;

    @Column("description")
    private String description;

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum TransactionType implements NumericEnum {
        CREDIT(0),
        DEBIT(1);

        private final int code;
    }

    /** Stored as SMALLINT - codes are a persisted contract, never renumber. */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public enum ReferenceType implements NumericEnum {
        /** Seller earnings: escrow released after buyer protection ended. */
        ESCROW_RELEASE(0),
        /** Buyer refund from a dispute resolution. */
        DISPUTE_REFUND(1),
        /** Buyer refund from an approved item return. */
        RETURN_REFUND(2),
        /** Manual staff adjustment. */
        ADJUSTMENT(3),
        /** Future: withdrawal to a bank account. */
        WITHDRAWAL(4);

        private final int code;
    }
}
