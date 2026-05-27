package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/** Frankfurter API response: { "base": "USD", "date": "2024-01-15", "rates": { "EUR": 0.92, ... } } */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForexRateResponse {
    private String base;
    private String date;
    private Map<String, BigDecimal> rates;
}
