package com.ecommerce.dto;

import com.ecommerce.model.Dispute;
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
public class DisputeDto {
    private UUID id;
    private UUID escrowTransactionId;
    private UUID orderId;
    private UUID orderItemId;
    private UUID openedByUserId;
    private String openedByName;
    private UUID sellerId;
    private String sellerName;
    private BigDecimal escrowAmount;
    private BigDecimal escrowRefundedAmount;
    private String reason;
    private Dispute.DisputeStatus status;
    private LocalDateTime escalatedAt;
    private UUID resolvedByUserId;
    private String resolutionNote;
    private BigDecimal refundAmount;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
