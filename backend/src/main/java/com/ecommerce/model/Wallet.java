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

/**
 * In-app store of value, one per user. Used as:
 *  - the refund destination when the original payment method cannot receive
 *    refunds (cash / one-time methods, unavailable gateway), and
 *  - the destination for seller earnings when escrow is released.
 * Every balance change is journaled in wallet_transactions.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("wallets")
public class Wallet extends BaseEntity {

    @Column("user_id")
    private UUID userId;

    @Column("balance")
    private BigDecimal balance;

    @Column("currency_code")
    private String currencyCode;
}
