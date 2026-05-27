package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayDto {
    private String id;
    private String displayName;
    private String description;
    private String iconClass;
    /** True when the gateway has real credentials and can process payments. */
    private boolean configured;
}
