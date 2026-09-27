package com.example.coffeeshop.boundary.error;

import com.example.coffeeshop.control.exception.CoffeeAlreadyExistsException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code CoffeeAlreadyExistsException} -> {@code 409} with {@code detail}
 * {@code Coffee name '{name}' already exists} (spec section 3.5). Raised by the uniqueness
 * pre-check and by the unique-constraint violation on flush.
 */
@Provider
public class CoffeeAlreadyExistsExceptionMapper implements ExceptionMapper<CoffeeAlreadyExistsException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(CoffeeAlreadyExistsException exception) {
        return ProblemResponses.problem(uriInfo, 409, exception.getMessage());
    }
}
