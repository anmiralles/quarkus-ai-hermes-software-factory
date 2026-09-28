package com.example.coffeeshop.boundary.dto;

import java.util.List;

import com.example.coffeeshop.control.CoffeePage;

/**
 * Read model for {@code GET /coffees} (spec section 3.2). {@code page}/{@code size} echo the
 * effective values used, so a defaulted request is visible to the client, and
 * {@code totalPages} is {@code ceil(totalElements / size)}, or {@code 0} for an empty result.
 */
public record CoffeePageResponse(
        List<CoffeeResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static CoffeePageResponse of(CoffeePage page) {
        long totalElements = page.totalElements();
        int size = page.size();
        return new CoffeePageResponse(
                page.content().stream().map(CoffeeResponse::from).toList(),
                page.page(),
                size,
                totalElements,
                totalPages(totalElements, size));
    }

    private static int totalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }
}
