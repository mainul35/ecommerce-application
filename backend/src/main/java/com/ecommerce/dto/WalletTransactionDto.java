package com.ecommerce.dto;

import com.ecommerce.model.WalletTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDto {
    private UUID id;
    private WalletTransaction.TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private WalletTransaction.ReferenceType referenceType;
    private UUID referenceId;
    private String description;
    private LocalDateTime createdAt;
}
