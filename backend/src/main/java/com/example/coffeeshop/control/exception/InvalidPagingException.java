package com.example.coffeeshop.control.exception;

/**
 * Bad paging request: {@code page < 0} or {@code size} outside {@code 1..100} (spec section 5).
 *
 * <p>Extends {@link IllegalArgumentException} because the control contract pins that type for
 * bad paging; the subtype additionally carries the offending field so the boundary mapper can
 * emit a per-field {@code errors[]} entry instead of guessing it from the message.
 *
 * <p>Paging is rejected, never clamped (spec section 3.1).
 */
public class InvalidPagingException extends IllegalArgumentException {

    private final String field;

    public InvalidPagingException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** JSON field name the boundary reports: {@code page} or {@code size}. */
    public String getField() {
        return field;
    }
}
