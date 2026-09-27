package com.example.coffeeshop.boundary.error;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Quarkus's Jackson body reader wraps a JSON <em>syntax</em> error in a
 * {@link WebApplicationException} with status {@code 400}, so the Jackson type never reaches the
 * mapper chain directly. This mapper unwraps exactly that case into the contract's problem detail
 * and hands every other {@code WebApplicationException} back untouched, so framework behaviour
 * (405, 415, an unmapped 404 route) is unchanged.
 */
@Provider
public class WrappedJsonExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonProcessingException jackson) {
                return JacksonBodyErrors.problem(uriInfo, jackson);
            }
        }
        return exception.getResponse();
    }
}
