package com.example.coffeeshop.control.exception;

/**
 * The name is already taken. Thrown by {@code control} and mapped to {@code 409} by
 * {@code boundary} (spec section 3.5): {@code detail} is
 * {@code Coffee name '{name}' already exists}.
 *
 * <p>Raised both by the uniqueness pre-check and by a unique-constraint violation on flush,
 * which is the part of the race the pre-check cannot close (spec section 5).
 */
public class CoffeeAlreadyExistsException extends RuntimeException {

    private final String name;

    public CoffeeAlreadyExistsException(String name) {
        super("Coffee name '" + name + "' already exists");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
