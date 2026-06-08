package com.ecommerce.exception;

/**
 * Thrown when an action requires a fully verified account (email + phone)
 * but the caller has not completed verification. Mapped to HTTP 403 so the
 * frontend can distinguish "you must verify first" from auth failures and
 * surface the verify-account prompt.
 */
public class VerificationRequiredException extends RuntimeException {
    public VerificationRequiredException(String message) {
        super(message);
    }
}
