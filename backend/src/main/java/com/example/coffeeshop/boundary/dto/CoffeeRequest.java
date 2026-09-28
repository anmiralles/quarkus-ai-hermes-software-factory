package com.example.coffeeshop.boundary.dto;

import java.math.BigDecimal;

import com.example.coffeeshop.entity.Coffee;
import com.example.coffeeshop.entity.RoastLevel;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Write model for {@code POST /coffees} and {@code PUT /coffees/{id}} (spec section 3.2).
 *
 * <p>No {@code id} and no timestamps: the server owns them, and with
 * {@code quarkus.jackson.fail-on-unknown-properties=true} a client that sends one gets a 400
 * rather than having it silently ignored (spec section 3.4).
 *
 * <p>Messages are the strings the contract pins in spec section 3.3.
 */
public record CoffeeRequest(
        @NotBlank(message = "name must not be blank")
        @Size(min = 1, max = 100, message = "name size must be between 1 and 100")
        String name,

        @NotNull(message = "roastLevel must be one of LIGHT, MEDIUM, DARK")
        RoastLevel roastLevel,

        @NotBlank(message = "origin must not be blank")
        @Size(min = 1, max = 100, message = "origin size must be between 1 and 100")
        String origin,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0", inclusive = false, message = "price must be greater than 0")
        @DecimalMax(value = "99999999.99", message = "price must be at most 99999999.99")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        // Integer, not int: a missing field must be a 400, and a primitive would default to 0
        @NotNull(message = "stock must not be null")
        @Min(value = 0, message = "stock must be greater than or equal to 0")
        Integer stock) {

    /**
     * Request -> entity. This is the write half of the boundary/entity mapping exception in
     * spec section 1.2; the column list is deliberately the mutable one only.
     */
    public Coffee toEntity() {
        Coffee coffee = new Coffee();
        coffee.setName(name);
        coffee.setRoastLevel(roastLevel);
        coffee.setOrigin(origin);
        coffee.setPrice(price);
        coffee.setStock(stock == null ? 0 : stock);
        return coffee;
    }
}
