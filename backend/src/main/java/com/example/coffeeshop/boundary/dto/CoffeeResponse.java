package com.example.coffeeshop.boundary.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.example.coffeeshop.entity.Coffee;
import com.example.coffeeshop.entity.RoastLevel;

/**
 * Read model for one coffee (spec section 3.2). The only place an entity is turned into
 * something that crosses the HTTP edge — the entity itself never appears in a {@code @Path}
 * signature (spec section 1.1, rule 3).
 */
public record CoffeeResponse(
        UUID id,
        String name,
        RoastLevel roastLevel,
        String origin,
        BigDecimal price,
        int stock,
        Instant createdAt,
        Instant updatedAt) {

    /** The contract serialises money at scale 2 ({@code 12.30}, never {@code 12.3}). */
    static final int PRICE_SCALE = 2;

    public static CoffeeResponse from(Coffee coffee) {
        return new CoffeeResponse(
                coffee.getId(),
                coffee.getName(),
                coffee.getRoastLevel(),
                coffee.getOrigin(),
                atContractScale(coffee.getPrice()),
                coffee.getStock(),
                coffee.getCreatedAt(),
                coffee.getUpdatedAt());
    }

    /**
     * Widens to scale 2 without rounding. {@link RoundingMode#UNNECESSARY} is safe because bean
     * validation has already rejected anything with more than 2 significant decimals; a value
     * that needed rounding would fail loudly here instead of being silently rounded (spec
     * sections 2 and 3.2 forbid rounding).
     */
    private static BigDecimal atContractScale(BigDecimal price) {
        return price == null ? null : price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }
}
