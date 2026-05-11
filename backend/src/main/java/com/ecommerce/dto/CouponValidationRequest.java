package com.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Customer-side request: "given this code and these cart items, what would I save?".
 * The same items list is what would later be POSTed to /api/orders.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationRequest {

    @NotBlank(message = "Code is required")
    private String code;

    @NotEmpty(message = "Items must not be empty")
    @Valid
    private List<OrderItemRequest> items;
}
