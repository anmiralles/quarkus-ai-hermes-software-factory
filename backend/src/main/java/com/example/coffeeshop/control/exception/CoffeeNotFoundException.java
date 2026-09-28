package com.example.coffeeshop.control.exception;

import java.util.UUID;

/**
 * No coffee exists for the requested id. Thrown by {@code control} and mapped to {@code 404}
 * by {@code boundary} (spec section 3.5): {@code detail} is {@code Coffee {id} not found}.
 */
public class CoffeeNotFoundException extends RuntimeException {

    private final String id;

    public CoffeeNotFoundException(UUID id) {
        this(String.valueOf(id));
    }

    public CoffeeNotFoundException(String id) {
        super("Coffee " + id + " not found");
        this.id = id;
    }

    /** The id as it was addressed, so a malformed (non-UUID) id is reported verbatim. */
    public String getId() {
        return id;
    }
}
