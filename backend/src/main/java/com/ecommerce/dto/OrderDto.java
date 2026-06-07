package com.ecommerce.dto;

import com.ecommerce.model.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private UUID id;
    private UUID userId;
    private List<OrderItemDto> items;
    private Order.OrderStatus status;
    private Order.PaymentStatus paymentStatus;
    private BigDecimal subtotalAmount;
    private String couponCode;
    private BigDecimal couponDiscountAmount;
    private BigDecimal totalAmount;
    private AddressDto shippingAddress;
    private AddressDto billingAddress;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
