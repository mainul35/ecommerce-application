package com.ecommerce.dto;

import com.ecommerce.model.ReturnRequest;
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
public class ReturnRequestDto {
    private UUID id;
    private UUID orderId;
    private UUID orderItemId;
    private UUID escrowTransactionId;
    private String productName;
    private String productImage;
    private Integer quantity;
    private String reason;
    private ReturnRequest.ReturnStatus status;
    private BigDecimal refundAmount;
    private ReturnRequest.RefundDestination refundDestination;
    private String rejectionReason;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
