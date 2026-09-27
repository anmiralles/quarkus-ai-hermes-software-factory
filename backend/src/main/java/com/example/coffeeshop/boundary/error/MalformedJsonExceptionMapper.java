package com.example.coffeeshop.boundary.error;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * A syntactically broken request body -> {@code 400} with {@code detail}
 * {@code Malformed request body} (spec section 3.5). The mapper sees the Jackson failure directly
 * only when Quarkus does not wrap it; {@link WrappedJsonExceptionMapper} covers the wrapped case.
 */
@Provider
public class MalformedJsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(JsonProcessingException exception) {
        return JacksonBodyErrors.problem(uriInfo, exception);
    }
}
