package com.example.coffeeshop.boundary.error;

import com.example.coffeeshop.control.exception.CoffeeNotFoundException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code CoffeeNotFoundException} -> {@code 404} with {@code detail} {@code Coffee {id} not found}
 * (spec section 3.5). Used for an unknown id and for a non-UUID path segment, which is not an
 * addressable resource in this API (spec section 3.1).
 */
@Provider
public class CoffeeNotFoundExceptionMapper implements ExceptionMapper<CoffeeNotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(CoffeeNotFoundException exception) {
        return ProblemResponses.problem(uriInfo, 404, exception.getMessage());
    }
}
