package com.ecommerce.dto;

import com.ecommerce.model.EscrowTransaction;
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
public class EscrowTransactionDto {
    private UUID id;
    private UUID orderId;
    private UUID sellerId;
    private String sellerName;
    private BigDecimal amount;
    private BigDecimal refundedAmount;
    private String currencyCode;
    private EscrowTransaction.EscrowStatus status;
    private String gatewayId;
    private LocalDateTime holdUntil;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
}
