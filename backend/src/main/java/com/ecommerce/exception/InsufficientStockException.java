package com.ecommerce.exception;

/**
 * Raised when an order can't be reserved because available stock
 * (products.stock - active reservations) is below the requested quantity.
 * Mapped to HTTP 409 Conflict by the global exception handler.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
