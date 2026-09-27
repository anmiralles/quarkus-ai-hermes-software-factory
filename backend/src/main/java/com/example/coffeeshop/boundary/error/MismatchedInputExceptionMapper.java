package com.example.coffeeshop.boundary.error;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Jackson deserialisation failure of a request body -> {@code 400} problem detail (spec sections
 * 3.3, 3.5) instead of a 500 or a vendor-specific body. Registered for the type Quarkus itself
 * maps (an unknown property or a wrongly typed field arrives here unwrapped), so this mapper wins
 * over the built-in one.
 */
@Provider
public class MismatchedInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return JacksonBodyErrors.problem(uriInfo, exception);
    }
}
